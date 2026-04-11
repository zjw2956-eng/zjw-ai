package cn.zjw.service.impl;

import cn.zjw.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.common.enums.OrderStatus;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import cn.zjw.common.utils.SensitiveWordUtil;
import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.zjw.mapper.ReviewMapper;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.enums.ReviewStatus;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.mapper.RestaurantMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.zjw.pojo.dto.ReviewQueryDTO;
import cn.zjw.pojo.vo.MyReviewVO;
import cn.zjw.pojo.vo.ReviewVO;
import cn.zjw.pojo.entity.User;
import cn.zjw.mapper.UserMapper;
import cn.zjw.mq.event.ReviewRatingRefreshEvent;
import cn.zjw.mq.event.ReviewCreatedEvent;

@Service
@Slf4j
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SensitiveWordUtil sensitiveWordUtil;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private CacheClient cacheClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReview(ReviewDTO dto) {
        // 获取当前用户ID
        Long userId = UserContext.getCurrentUserId();

        // 获取该订单对应的餐厅ID
        OrderInfo orderInfo = orderMapper.selectById(dto.getOrderId());
        if ((orderInfo == null) || (orderInfo.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!orderInfo.getStatus().equals(OrderStatus.COMPLETED.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单状态不支持评价");
        }
        if (!orderInfo.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "这不是你的订单，无评价权限");
        }

        String lockKey = Constants.REDIS_LOCK_REVIEW_SUBMIT + userId + dto.getOrderId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(
                    Constants.LOCK_GET_TIME,
                    Constants.LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS); // 抢锁：不等待，拿不到立即返回 false，持有10秒
            if (!locked) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请勿重复提交评价");
            }
            // 校验是否已评价，一个订单只能评价一次
            LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Review::getOrderId, dto.getOrderId());
            wrapper.eq(Review::getUserId, userId);
            wrapper.eq(Review::getIsDeleted, 0);
            Review existReview = reviewMapper.selectOne(wrapper);
            if (existReview != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "该订单已评价，不能重复评价");
            }
            // 获取订单对应的餐厅ID
            Long restaurantId = orderInfo.getRestaurantId();
            // [敏感词过滤] 混合方案：DFA本地快速过滤 + AI语义审核（异步）
            // 第一层：DFA算法快速检测（本地内存，<10ms）
            // 1. 维护敏感词库（Redis Set存储，key: sensitive:words）
            // 2. 使用DFA算法匹配 dto.getContent()
            // 3. 命中明显敏感词 → 直接抛异常拒绝发表
            // 工具：Hutool的SensitiveUtil 或 自己实现DFA（面试加分）
            List<String> sensitiveWords = sensitiveWordUtil.check(dto.getContent());
            log.info("DFA过滤敏感词...");
            if (!sensitiveWords.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "评价内容包含敏感词");
            }
            // 未命中敏感词，构建评价实体
            log.info("未命中敏感词，构建评价实体...");
            // 构建评价实体
            Review review = new Review();
            BeanUtil.copyProperties(dto, review);
            // 将图片列表转换为JSON字符串
            review.setImages(JSONUtil.toJsonStr(dto.getImages()));

            review.setUserId(userId);
            review.setRestaurantId(restaurantId);
            // 设置评价状态为待审核
            review.setStatus(ReviewStatus.PENDING.getCode());
            // 保存评价
            log.info("保存评价...");
            reviewMapper.insert(review);
            // 第二层：AI语义审核（异步，不阻塞用户）
            // 1. 未命中DFA → 设置状态为 PENDING（待审核）
            // 2. 发送MQ消息到 review.audit.queue
            // 3. 消费者调用通义千问API审核：
            // Prompt: "判断以下评价是否包含辱骂/政治敏感/色情/广告，返回JSON: {safe:true/false, reason:''}"
            // 4. AI返回结果：
            // - safe=true → 自动调用 approveReview(reviewId)
            // - safe=false → 保持PENDING状态，等待人工复核
            // 不直接发MQ而是发布事件
            eventPublisher.publishEvent(new ReviewCreatedEvent(this, review.getId()));
            log.info("已发布评价创建事件，等待事务提交后发送MQ");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("提交评价被中断", e);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveReview(Long id) {
        // 查询评价是否存在
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评价不存在");
        }
        if (!review.getStatus().equals(ReviewStatus.PENDING.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评价状态不支持审核");
        }
        // 更新评价状态
        review.setStatus(ReviewStatus.APPROVED.getCode());
        reviewMapper.updateById(review);

        // 删除缓存
        String cacheKey = Constants.REDIS_REVIEW_DETAIL + review.getUserId() + ":" + id;
        cacheClient.delete(cacheKey);
        // 删除餐厅详情缓存
        String restaurantCacheKey = Constants.REDIS_RESTAURANT_KEY + review.getRestaurantId();
        cacheClient.delete(restaurantCacheKey);
        // 删除餐厅AI摘要缓存（有新评价通过后触发刷新）
        String restaurantSummaryCacheKey = Constants.REDIS_RESTAURANT_SUMMARY_KEY + review.getRestaurantId();
        cacheClient.delete(restaurantSummaryCacheKey);

        // 发布审核评价通过事件
        eventPublisher.publishEvent(new ReviewRatingRefreshEvent(this, review.getRestaurantId()));
        // String msgId = IdUtil.simpleUUID();
        // // 异步更新评分
        // rabbitTemplate.convertAndSend(
        // "review.exchange",
        // "review.approved",
        // review.getRestaurantId(),
        // msg -> {
        // msg.getMessageProperties().setMessageId(msgId);
        // return msg;
        // });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectReview(Long id) {
        // 查询评价是否存在
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评价不存在");
        }
        if (!review.getStatus().equals(ReviewStatus.PENDING.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "评价状态不支持审核");
        }
        // 更新评价状态
        review.setStatus(ReviewStatus.REJECTED.getCode());
        reviewMapper.updateById(review);

        // 删除缓存
        String cacheKey = Constants.REDIS_REVIEW_DETAIL + review.getUserId() + ":" + id;
        cacheClient.delete(cacheKey);
    }

    @Override
    public Page<ReviewVO> listByRestaurantId(ReviewQueryDTO dto) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getRestaurantId, dto.getRestaurantId())
                .eq(Review::getIsDeleted, 0)
                .eq(Review::getStatus, ReviewStatus.APPROVED.getCode())
                .eq(dto.getRating() != null, Review::getRating, dto.getRating())
                .orderByDesc(Review::getCreateTime);
        Page<Review> firstPage = this.page(new Page<>(dto.getCurrent(), dto.getPageSize()), wrapper);

        // 2. 转换为VO，要关联查询用户表获取用户昵称和头像
        // 获取评价列表
        List<Review> reviewList = firstPage.getRecords();
        // 空分页判断
        if (reviewList.isEmpty()) {
            Page<ReviewVO> emptyPage = new Page<>(firstPage.getCurrent(), firstPage.getSize(), 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        // 提取所有userId
        Set<Long> userIds = reviewList.stream().map(Review::getUserId).collect(Collectors.toSet());

        // 批量查询用户(只查询一次数据库)
        List<User> userList = userMapper.selectBatchIds(userIds);

        // 构建Map映射
        Map<Long, User> userMap = userList.stream().collect(Collectors.toMap(User::getId, u -> u));

        // 组装VO
        List<ReviewVO> voList = reviewList.stream()
                .map(review -> {
                    ReviewVO vo = new ReviewVO();
                    BeanUtil.copyProperties(review, vo);
                    // 将图片列表转换为List<String>
                    vo.setImages(JSONUtil.toList(review.getImages(), String.class));
                    // 从Map中获取用户信息
                    User user = userMap.get(review.getUserId());
                    if (user != null) {
                        vo.setUserNickname(user.getNickname());
                        vo.setUserAvatar(user.getAvatar());
                    }
                    return vo;
                }).collect(Collectors.toList());

        // 构造结果分页
        Page<ReviewVO> resultPage = new Page<>(
                firstPage.getCurrent(),
                firstPage.getSize(),
                firstPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public Page<MyReviewVO> listMyReviews(Integer current, Integer pageSize) {
        Long userId = UserContext.getCurrentUserId();
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreateTime);
        Page<Review> firstPage = this.page(new Page<>(current, pageSize), wrapper);

        List<Review> reviewList = firstPage.getRecords();
        // 空分页判断
        if (reviewList.isEmpty()) {
            Page<MyReviewVO> emptyPage = new Page<>(firstPage.getCurrent(), firstPage.getSize(), 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }
        // 关联查询餐厅名和订单号
        // 提取所有餐厅ID，并进行批量查询
        Set<Long> restaurantIds = reviewList.stream().map(Review::getRestaurantId).collect(Collectors.toSet());
        List<Restaurant> restaurantList = restaurantMapper.selectBatchIds(restaurantIds);

        // 提取所有订单ID，并进行批量查询
        Set<Long> orderIds = reviewList.stream().map(Review::getOrderId).collect(Collectors.toSet());
        List<OrderInfo> orderList = orderMapper.selectBatchIds(orderIds);

        // 构建Map映射
        // 餐厅ID -> Restaurant
        Map<Long, Restaurant> restaurantMap = restaurantList
                .stream().collect(Collectors.toMap(Restaurant::getId, r -> r));
        // 订单ID -> OrderInfo
        Map<Long, OrderInfo> orderMap = orderList
                .stream().collect(Collectors.toMap(OrderInfo::getId, o -> o));

        // 组装VO
        List<MyReviewVO> voList = reviewList.stream()
                .map(review -> {
                    MyReviewVO vo = new MyReviewVO();
                    BeanUtil.copyProperties(review, vo);
                    // 将图片列表转换为List<String>
                    vo.setImages(JSONUtil.toList(review.getImages(), String.class));
                    // 从Map中获取餐厅信息
                    Restaurant restaurant = restaurantMap.get(review.getRestaurantId());
                    if (restaurant != null) {
                        vo.setRestaurantName(restaurant.getName());
                    }
                    // 从Map中获取订单信息
                    OrderInfo order = orderMap.get(review.getOrderId());
                    if (order != null) {
                        vo.setOrderNo(order.getOrderNo());
                    }
                    vo.setStatusDesc(ReviewStatus.getDescByCode(review.getStatus()));
                    return vo;
                }).collect(Collectors.toList());
        // 构造结果分页
        Page<MyReviewVO> resultPage = new Page<>(
                firstPage.getCurrent(),
                firstPage.getSize(),
                firstPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public MyReviewVO getReviewDetail(Long id) {
        Long userId = UserContext.getCurrentUserId();
        MyReviewVO result = cacheClient.queryWithPassThrough(
                Constants.REDIS_REVIEW_DETAIL + userId + ":",
                id,
                MyReviewVO.class,
                this::buildMyReviewVO,
                Constants.REDIS_EXPIRE_TIME,
                Constants.REDIS_EMPTY_KEY_EXPIRE_TIME,
                "review");
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评价不存在");
        }
        return result;
    }

    /**
     * 构造MyReviewVO
     * 
     * @param id
     * @return
     */
    private MyReviewVO buildMyReviewVO(Long id) {
        Long userId = UserContext.getCurrentUserId();
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            return null;
        }
        if (!review.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限查看该评价");
        }
        Restaurant restaurant = restaurantMapper.selectById(review.getRestaurantId());
        if (restaurant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "餐厅不存在");
        }
        OrderInfo orderInfo = orderMapper.selectById(review.getOrderId());
        if (orderInfo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }

        MyReviewVO vo = new MyReviewVO();
        BeanUtil.copyProperties(review, vo);
        vo.setImages(JSONUtil.toList(review.getImages(), String.class));
        vo.setRestaurantName(restaurant.getName());
        vo.setOrderNo(orderInfo.getOrderNo());
        vo.setStatusDesc(ReviewStatus.getDescByCode(review.getStatus()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评价不存在");
        }
        if (!review.getUserId().equals(UserContext.getCurrentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权限删除该评价");
        }

        // 先记录状态和餐厅ID，避免逻辑删除后对象状态使用混乱
        Integer status = review.getStatus();
        Long restaurantId = review.getRestaurantId();

        // 删除评价，调用 MyBatis-Plus 逻辑删除
        reviewMapper.deleteById(id);

        // 删除缓存
        String cacheKey = Constants.REDIS_REVIEW_DETAIL + review.getUserId() + ":" + id;
        cacheClient.delete(cacheKey);

        // 删除餐厅详情缓存
        String restaurantCacheKey = Constants.REDIS_RESTAURANT_KEY + restaurantId;
        cacheClient.delete(restaurantCacheKey);

        // 删除餐厅AI摘要缓存
        String restaurantSummaryCacheKey = Constants.REDIS_RESTAURANT_SUMMARY_KEY + restaurantId;
        cacheClient.delete(restaurantSummaryCacheKey);

        // 如果删除的是已审核通过评价，则事务提交后异步更新餐厅评分
        if (ReviewStatus.APPROVED.getCode().equals(status)) {
            eventPublisher.publishEvent(new ReviewRatingRefreshEvent(this, restaurantId));
        }
    }

    @Override
    public List<MyReviewVO> getUserReviewHistory(Long userId, Integer limit) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getIsDeleted, 0)
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreateTime)
                .last("LIMIT " + (limit == null ? 10 : limit));
        List<Review> reviews = reviewMapper.selectList(wrapper);

        Set<Long> restaurantIds = reviews.stream().map(Review::getRestaurantId).collect(Collectors.toSet());
        if (restaurantIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Restaurant> restaurantList = restaurantMapper.selectBatchIds(restaurantIds);
        Map<Long, Restaurant> restaurantMap = restaurantList.stream()
                .collect(Collectors.toMap(Restaurant::getId, r -> r));

        List<MyReviewVO> voList = reviews.stream().map(review -> {
            MyReviewVO vo = new MyReviewVO();
            BeanUtil.copyProperties(review, vo);
            // 将图片列表转换为List<String>
            if (StrUtil.isNotBlank(review.getImages())) {
                vo.setImages(JSONUtil.toList(review.getImages(), String.class));
            }
            // 将餐厅ID转换为餐厅名
            Restaurant restaurant = restaurantMap.get(review.getRestaurantId());
            if (restaurant != null) {
                vo.setRestaurantName(restaurant.getName());
            }
            return vo;
        }).collect(Collectors.toList());
        return voList;
    }

}
