package cn.zjw.service.impl;

import cn.zjw.service.ReviewService;
import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import cn.zjw.pojo.entity.Review;
import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.common.enums.OrderStatus;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import cn.zjw.common.context.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.zjw.mapper.ReviewMapper;
import cn.hutool.core.bean.BeanUtil;
import cn.zjw.common.enums.ReviewStatus;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.mapper.RestaurantMapper;
import org.springframework.transaction.annotation.Transactional;
import cn.zjw.pojo.entity.User;
import cn.zjw.mapper.UserMapper;
import java.util.List;
import java.util.Set;
import java.util.Map;
import cn.zjw.pojo.vo.ReviewVO;

@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {
    

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void createReview(ReviewDTO dto){
        //获取当前用户ID
        Long userId= UserContext.getCurrentUserId();

        //获取该订单对应的餐厅ID
        OrderInfo orderInfo=orderMapper.selectById(dto.getOrderId());
        if ((orderInfo == null) || (orderInfo.getIsDeleted()== 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"订单不存在");
        }
        if(!orderInfo.getStatus().equals(OrderStatus.COMPLETED.getCode())){
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"订单状态不支持评价");
        }
        if(!orderInfo.getUserId().equals(userId)){
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),"这不是你的订单，无评价权限");
        }
        //校验是否已评价，一个订单只能评价一次
        LambdaQueryWrapper<Review> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(Review::getOrderId, dto.getOrderId());
        wrapper.eq(Review::getUserId, userId);
        wrapper.eq(Review::getIsDeleted, 0);
        Review existReview=reviewMapper.selectOne(wrapper);
        if(existReview != null){
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"该订单已评价，不能重复评价");
        }
        //获取订单对应的餐厅ID
        Long restaurantId=orderInfo.getRestaurantId();

        //构建评价实体
        Review review=new Review();
        BeanUtil.copyProperties(dto,review);
        review.setUserId(userId);
        review.setRestaurantId(restaurantId);

        // TODO: [敏感词过滤] 混合方案：DFA本地快速过滤 + AI语义审核（异步）
        //   第一层：DFA算法快速检测（本地内存，<10ms）
        //     1. 维护敏感词库（Redis Set存储，key: sensitive:words）
        //     2. 使用DFA算法匹配 dto.getContent()
        //     3. 命中明显敏感词 → 直接抛异常拒绝发表
        //     工具：Hutool的SensitiveUtil 或 自己实现DFA（面试加分）
        //
        //   第二层：AI语义审核（异步，不阻塞用户）
        //     1. 未命中DFA → 设置状态为 PENDING（待审核）
        //     2. 发送MQ消息到 review.audit.queue
        //     3. 消费者调用通义千问API审核：
        //        Prompt: "判断以下评价是否包含辱骂/政治敏感/色情/广告，返回JSON: {safe:true/false, reason:''}"
        //     4. AI返回结果：
        //        - safe=true → 自动调用 approveReview(reviewId)
        //        - safe=false → 保持PENDING状态，等待人工复核
        //
        //   优势：90%正常评价快速放行，10%可疑内容AI精准判断

        //设置评价状态为待审核
        review.setStatus(ReviewStatus.PENDING.getCode());

        //保存评价
        reviewMapper.insert(review);
        // TODO: [RabbitMQ] 通知管理员/商家有新评价待审核
        //   交换机: review.exchange，routing key: review.pending
        //   消息体: { reviewId: review.getId(), restaurantId }
        //   消费者: 向管理员/商家推送"新评价待审核"通知
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void approveReview(Long id){
        //查询评价是否存在
        Review review=reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"评价不存在");
        }
        if(!review.getStatus().equals(ReviewStatus.PENDING.getCode())){
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"评价状态不支持审核");
        }
        //更新评价状态
        review.setStatus(ReviewStatus.APPROVED.getCode());
        reviewMapper.updateById(review);

        // 同步更新评分（后续改为 MQ 异步）
        updateRestaurantRating(review.getRestaurantId());
        // TODO: [RabbitMQ] 评分更新：后续替换为发送 MQ 消息，消费者异步执行 updateRestaurantRating()
        //   交换机: review.exchange，routing key: review.approved
        //   消息体: { restaurantId }
        //   注意: 迁移时需将 updateRestaurantRating() 提取到独立的 RatingUpdateService Bean
        // TODO: [RabbitMQ] 通知用户评价已通过审核
        //   交换机: review.exchange，routing key: review.notify.user
        //   消息体: { userId: review.getUserId(), reviewId: id }
        //   消费者: 向用户推送"您的评价已通过审核"通知
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public void rejectReview(Long id){
        //查询评价是否存在
        Review review=reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"评价不存在");
        }
        if(!review.getStatus().equals(ReviewStatus.PENDING.getCode())){
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"评价状态不支持审核");
        }
        //更新评价状态
        review.setStatus(ReviewStatus.REJECTED.getCode());
        reviewMapper.updateById(review);
        // TODO: [RabbitMQ] 通知用户评价未通过审核
        //   交换机: review.exchange，routing key: review.notify.user
        //   消息体: { userId: review.getUserId(), reviewId: id }
        //   消费者: 向用户推送"您的评价未通过审核"通知
    }


    /**
     * 更新餐厅评分,私有方法
     * 计算餐厅的平均评分，并更新到餐厅表
     * // TODO: [RabbitMQ] 迁移时需将此方法提取到独立的 RatingUpdateService Bean 中
     * 原因：MQ消费者是独立Bean，无法调用当前类的 private 方法
     * 所以需要将此方法提取到独立的 RatingUpdateService Bean 中
     * 然后通过MQ消费者调用该方法
     * 这样就可以保证餐厅评分更新的一致性和可靠性
     * 同时也可以保证餐厅评分更新的及时性
     * 同时也可以保证餐厅评分更新的及时性
     * @param restaurantId 餐厅ID
     */
    private void updateRestaurantRating(Long restaurantId){
        BigDecimal avgRating= reviewMapper.getAvgRatingByRestaurantId(restaurantId);
        if (avgRating == null) {
            avgRating=BigDecimal.ZERO;
        }
        Restaurant restaurant=restaurantMapper.selectById(restaurantId);
        if (restaurant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"餐厅不存在");
        }
        restaurant.setRating(avgRating);
        restaurantMapper.updateById(restaurant);
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
                //从Map中获取用户信息
                User user=userMap.get(review.getUserId());
                if(user!=null){
                    vo.setUserNickname(user.getNickname());
                    vo.setUserAvatar(user.getAvatar());
                }
            }).collect(Collectors.toList());

        //构造结果分页
        Page<ReviewVO> resultPage = new Page<>(
            firstPage.getCurrent(),
            firstPage.getPageSize(),
            firstPage.getTotal()
        );
        resultPage.setRecords(voList);
        return resultPage;      
    }
}
