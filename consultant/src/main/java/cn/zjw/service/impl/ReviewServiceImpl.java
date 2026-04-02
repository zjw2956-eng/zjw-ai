package cn.zjw.service.impl;

import cn.zjw.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;

import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.common.enums.OrderStatus;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import cn.zjw.common.utils.SensitiveWordUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.zjw.mapper.ReviewMapper;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.enums.ReviewStatus;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.mapper.RestaurantMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
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
import cn.zjw.mq.message.ReviewAuditMessage;
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
    private RedisTemplate<String,Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate; // 引入RabbitTemplate，用于发送消息到队列

    @Autowired
    private SensitiveWordUtil sensitiveWordUtil;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    
    @Override
    @Transactional(rollbackFor=Exception.class)
    public void createReview(ReviewDTO dto){
        //获取当前用户ID
        Long userId= UserContext.getCurrentUserId();

        //获取该订单对应的餐厅ID
        OrderInfo orderInfo=orderMapper.selectById(dto.getOrderId());
        if ((orderInfo == null) || (orderInfo.getIsDeleted()== 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND,"订单不存在");
        }
        if(!orderInfo.getStatus().equals(OrderStatus.COMPLETED.getCode())){
            throw new BusinessException(ResultCode.BAD_REQUEST,"订单状态不支持评价");
        }
        if(!orderInfo.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.FORBIDDEN,"这不是你的订单，无评价权限");
        }
        //校验是否已评价，一个订单只能评价一次
        LambdaQueryWrapper<Review> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(Review::getOrderId, dto.getOrderId());
        wrapper.eq(Review::getUserId, userId);
        wrapper.eq(Review::getIsDeleted, 0);
        Review existReview=reviewMapper.selectOne(wrapper);
        if(existReview != null){
            throw new BusinessException(ResultCode.BAD_REQUEST,"该订单已评价，不能重复评价");
        }
        //获取订单对应的餐厅ID
        Long restaurantId=orderInfo.getRestaurantId();
        // [敏感词过滤] 混合方案：DFA本地快速过滤 + AI语义审核（异步）
        //   第一层：DFA算法快速检测（本地内存，<10ms）
        //     1. 维护敏感词库（Redis Set存储，key: sensitive:words）
        //     2. 使用DFA算法匹配 dto.getContent()
        //     3. 命中明显敏感词 → 直接抛异常拒绝发表
        //     工具：Hutool的SensitiveUtil 或 自己实现DFA（面试加分）
        List<String> sensitiveWords=sensitiveWordUtil.check(dto.getContent());
        log.info("DFA过滤敏感词...");
        if(!sensitiveWords.isEmpty()){
            throw new BusinessException(ResultCode.BAD_REQUEST,"评价内容包含敏感词");
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
        //保存评价
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
        log.info("构造消息体......");
        // 优势：90%正常评价快速放行，10%可疑内容AI精准判断
        ReviewAuditMessage message=new ReviewAuditMessage();
        message.setReviewId(review.getId());
        message.setContent(review.getContent());
        message.setRating(review.getRating());

        //构造一个CorrelationData,用于消息确认
        String msgId= IdUtil.simpleUUID();
        //消息体序列化，把消息按照随机UUID生产的msgId存到redis里面
        String msgJson=JSONUtil.toJsonStr(message);
        // 3. 发送前把消息存入Redis（附带初始重试次数 0）
        Map<String,Object> msgMap=new HashMap<>();
        msgMap.put("message", msgJson);
        msgMap.put("retryCount", 0);
        String msgStrMap=JSONUtil.toJsonStr(msgMap);
        redisTemplate.opsForValue().set(
                Constants.RABBITMQ_CORRELATION_MSG_ID+msgId,
                msgStrMap,
                Constants.MQ_RETRY_INTERVAL_TIME, TimeUnit.SECONDS
        );
        log.info("消息已存入Redis，msgId: {}", msgId);

        //不直接发MQ而是发布事件
        eventPublisher.publishEvent(new ReviewCreatedEvent(message,msgId));
        log.info("已发布评价创建事件，等待事务提交后发送MQ");
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void approveReview(Long id){
        //查询评价是否存在
        Review review=reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,"评价不存在");
        }
        if(!review.getStatus().equals(ReviewStatus.PENDING.getCode())){
            throw new BusinessException(ResultCode.BAD_REQUEST,"评价状态不支持审核");
        }
        //更新评价状态
        review.setStatus(ReviewStatus.APPROVED.getCode());
        reviewMapper.updateById(review);

        //删除缓存
        String cacheKey=Constants.REDIS_REVIEW_DETAIL + review.getUserId() + ":" + id;
        redisTemplate.delete(cacheKey);
        //删除餐厅详情缓存
        String restaurantCacheKey=Constants.REDIS_RESTAURANT_KEY + review.getRestaurantId();
        redisTemplate.delete(restaurantCacheKey);
        //删除餐厅AI摘要缓存（有新评价通过后触发刷新）
        String restaurantSummaryCacheKey = Constants.REDIS_RESTAURANT_SUMMARY_KEY + review.getRestaurantId();
        redisTemplate.delete(restaurantSummaryCacheKey);

        // 异步更新评分
        rabbitTemplate.convertAndSend("review.exchange", "review.approved", review.getRestaurantId());
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void rejectReview(Long id){
        //查询评价是否存在
        Review review=reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND,"评价不存在");
        }
        if(!review.getStatus().equals(ReviewStatus.PENDING.getCode())){
            throw new BusinessException(ResultCode.BAD_REQUEST,"评价状态不支持审核");
        }
        //更新评价状态
        review.setStatus(ReviewStatus.REJECTED.getCode());
        reviewMapper.updateById(review);

        //删除缓存
        String cacheKey=Constants.REDIS_REVIEW_DETAIL + review.getUserId() + ":" + id;
        redisTemplate.delete(cacheKey);
    }


   

    @Override
    public Page<ReviewVO> listByRestaurantId(ReviewQueryDTO dto){
        LambdaQueryWrapper<Review> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(Review::getRestaurantId, dto.getRestaurantId())
            .eq(Review::getIsDeleted, 0)
            .eq(Review::getStatus, ReviewStatus.APPROVED.getCode())
            .eq(dto.getRating()!=null, Review::getRating,dto.getRating())
            .orderByDesc(Review::getCreateTime);
        Page<Review> firstPage= this.page(new Page<>(dto.getCurrent(),dto.getPageSize()),wrapper);

        // 2. 转换为VO，要关联查询用户表获取用户昵称和头像
        //获取评价列表
        List<Review> reviewList=firstPage.getRecords();
        //空分页判断
        if (reviewList.isEmpty()) {
            Page<ReviewVO> emptyPage = new Page<>(firstPage.getCurrent(), firstPage.getSize(), 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        //提取所有userId
        Set<Long> userIds= reviewList.stream().map(Review::getUserId).collect(Collectors.toSet());

        //批量查询用户(只查询一次数据库)
        List<User> userList=userMapper.selectBatchIds(userIds);

        //构建Map映射
        Map<Long,User> userMap=userList.stream().collect(Collectors.toMap(User::getId,u->u));

        //组装VO
        List<ReviewVO> voList=reviewList.stream()
            .map(review->{
                ReviewVO vo=new ReviewVO();
                BeanUtil.copyProperties(review,vo);
                //将图片列表转换为List<String>
                vo.setImages(JSONUtil.toList(review.getImages(), String.class));
                //从Map中获取用户信息
                User user=userMap.get(review.getUserId());
                if(user!=null){
                    vo.setUserNickname(user.getNickname());
                    vo.setUserAvatar(user.getAvatar());
                }
                return vo;
            }).collect(Collectors.toList());

        //构造结果分页
        Page<ReviewVO> resultPage = new Page<>(
            firstPage.getCurrent(),
            firstPage.getSize(),
            firstPage.getTotal()
        );
        resultPage.setRecords(voList);
        return resultPage;      
    }

    @Override
    public Page<MyReviewVO> listMyReviews(Integer current, Integer pageSize) {
        Long userId= UserContext.getCurrentUserId();
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getUserId,userId)
            .orderByDesc(Review::getCreateTime);
        Page<Review> firstPage=this.page(new Page<>(current,pageSize),wrapper);

        List<Review> reviewList=firstPage.getRecords();
        //空分页判断
        if (reviewList.isEmpty()) {
            Page<MyReviewVO> emptyPage = new Page<>(firstPage.getCurrent(), firstPage.getSize(), 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }
        //关联查询餐厅名和订单号
        //提取所有餐厅ID，并进行批量查询
        Set<Long> restaurantIds=reviewList.stream().map(Review::getRestaurantId).collect(Collectors.toSet());
        List<Restaurant> restaurantList = restaurantMapper.selectBatchIds(restaurantIds);

        //提取所有订单ID，并进行批量查询
        Set<Long> orderIds=reviewList.stream().map(Review::getOrderId).collect(Collectors.toSet());
        List<OrderInfo> orderList=orderMapper.selectBatchIds(orderIds);

        //构建Map映射
        //餐厅ID -> Restaurant
        Map<Long,Restaurant> restaurantMap=restaurantList
            .stream().collect(Collectors.toMap(Restaurant::getId, r->r));
        //订单ID -> OrderInfo
        Map<Long,OrderInfo> orderMap = orderList
            .stream().collect(Collectors.toMap(OrderInfo::getId, o->o));

        //组装VO
        List<MyReviewVO> voList=reviewList.stream()
            .map(review->{
                MyReviewVO vo=new MyReviewVO();
                BeanUtil.copyProperties(review,vo);
                //将图片列表转换为List<String>
                vo.setImages(JSONUtil.toList(review.getImages(), String.class));
                //从Map中获取餐厅信息
                Restaurant restaurant=restaurantMap.get(review.getRestaurantId());
                if(restaurant!=null){
                    vo.setRestaurantName(restaurant.getName());
                }
                //从Map中获取订单信息
                OrderInfo order=orderMap.get(review.getOrderId());
                if(order!=null){
                    vo.setOrderNo(order.getOrderNo());
                }
                vo.setStatusDesc(ReviewStatus.getDescByCode(review.getStatus()));
                return vo;
            }).collect(Collectors.toList());
        //构造结果分页
        Page<MyReviewVO> resultPage=new Page<>(
            firstPage.getCurrent(),
            firstPage.getSize(),
            firstPage.getTotal()
        );
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public MyReviewVO getReviewDetail(Long id) {
        //获取当前用户ID
        Long userId= UserContext.getCurrentUserId();
        //查询缓存
        String cacheKey=Constants.REDIS_REVIEW_DETAIL + userId + ":" + id;
        MyReviewVO cachedMyReviewVO=(MyReviewVO) redisTemplate.opsForValue().get(cacheKey);
        if(cachedMyReviewVO!=null){
            return cachedMyReviewVO;
        }
        //缓存未命中，查询数据库
        //查询评价是否存在
        Review review=reviewMapper.selectById(id);
        if(review==null){
            throw new BusinessException(ResultCode.NOT_FOUND,"评价不存在");
        }
        //权限校验
        if(!review.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.FORBIDDEN,"无权限查看该评价");
        }
        //查询餐厅名
        Restaurant restaurant=restaurantMapper.selectById(review.getRestaurantId());
        if(restaurant==null){
            throw new BusinessException(ResultCode.NOT_FOUND,"餐厅不存在");
        }
        //查询订单号
        OrderInfo orderInfo=orderMapper.selectById(review.getOrderId());
        if(orderInfo==null){
            throw new BusinessException(ResultCode.NOT_FOUND,"订单不存在");
        }
        String restaurantName=restaurant.getName();
        String orderNo=orderInfo.getOrderNo();
        //构建VO
        MyReviewVO vo=new MyReviewVO();
        BeanUtil.copyProperties(review, vo);
        //将图片列表转换为List<String>
        vo.setImages(JSONUtil.toList(review.getImages(), String.class));
        vo.setRestaurantName(restaurantName);
        vo.setOrderNo(orderNo);
        vo.setStatusDesc(ReviewStatus.getDescByCode(review.getStatus()));

        //缓存VO
        redisTemplate.opsForValue().set(cacheKey, vo, Constants.REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id) {
        Review review=reviewMapper.selectById(id);
        if(review==null){
            throw new BusinessException(ResultCode.NOT_FOUND,"评价不存在");
        }
        if(!review.getUserId().equals(UserContext.getCurrentUserId())){
            throw new BusinessException(ResultCode.FORBIDDEN,"无权限删除该评价");
        }
        //删除评价,调用MybatisPlus的逻辑删除方法
        reviewMapper.deleteById(id);

        if (review.getStatus().equals(ReviewStatus.APPROVED.getCode())) {
            //更新餐厅评分
            // 异步更新评分
            rabbitTemplate.convertAndSend("review.exchange", "review.approved", review.getRestaurantId());
        }
        //删除缓存
        String cacheKey=Constants.REDIS_REVIEW_DETAIL + review.getUserId() + ":" + id;
        redisTemplate.delete(cacheKey);
        //删除餐厅详情缓存
        String restaurantCacheKey=Constants.REDIS_RESTAURANT_KEY + review.getRestaurantId();
        redisTemplate.delete(restaurantCacheKey);
    }

    @Override
    public List<MyReviewVO> getUserReviewHistory(Long userId, Integer limit) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getIsDeleted, 0)
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreateTime)
                .last("LIMIT " + (limit == null ? 10 : limit));
        List<Review> reviews = reviewMapper.selectList(wrapper);

        List<MyReviewVO> voList = reviews.stream().map(review -> {
            MyReviewVO vo = new MyReviewVO();
            BeanUtil.copyProperties(review, vo);
            // 将图片列表转换为List<String>
            if (StrUtil.isNotBlank(review.getImages())) {
                vo.setImages(JSONUtil.toList(review.getImages(), String.class));
            }
            // 将餐厅ID转换为餐厅名
            Restaurant restaurant = restaurantMapper.selectById(review.getRestaurantId());
            if (restaurant != null) {
                vo.setRestaurantName(restaurant.getName());
            }
            return vo;
        }).collect(Collectors.toList());
        return voList;
    }
        

}
