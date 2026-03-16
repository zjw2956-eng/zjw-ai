package cn.zjw.service.impl;

import cn.zjw.service.ReviewService;
import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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


@Service
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {
    

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private RestaurantMapper restaurantMapper;

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
}
