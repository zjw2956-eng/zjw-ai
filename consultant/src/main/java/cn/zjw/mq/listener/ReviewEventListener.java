package cn.zjw.mq.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.mapper.ReviewMapper;
import cn.zjw.mq.event.ReviewRatingRefreshEvent;
import cn.zjw.mq.event.ReviewCreatedEvent;
import cn.zjw.mq.message.ReviewRatingRefreshMessage;
import cn.zjw.mq.message.ReviewAuditMessage;
import cn.zjw.pojo.entity.Review;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReviewEventListener {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ReviewMapper reviewMapper;

    /**
     * 监听评价创建事件，在事务提交后发送 MQ 消息
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewCreated(ReviewCreatedEvent event) {
        log.info("评价创建事务提交，发送MQ消息......");
        Long reviewId = event.getReviewId();
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Review::getId, reviewId);
        Review review = reviewMapper.selectOne(wrapper);
        if (review == null) {
            throw new RuntimeException("评价不存在");
        }
        String content = review.getContent();
        Integer rating = review.getRating();
        log.info("构造消息体......");
        ReviewAuditMessage message = new ReviewAuditMessage(reviewId, content, rating);
        // 构造一个CorrelationData,用于消息确认
        String msgId = IdUtil.simpleUUID();
        // 消息体序列化
        String msgJson = JSONUtil.toJsonStr(message);
        // 3. 发送前把消息存入Redis（附带初始重试次数 0）
        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put(Constants.MQ_EXCHANGE, "review.exchange");
        msgMap.put(Constants.MQ_ROUTING_KEY, "review.audit");
        msgMap.put(Constants.MQ_MESSAGE, msgJson);
        msgMap.put(Constants.MQ_RETRY_COUNT, 0);
        String msgStrMap = JSONUtil.toJsonStr(msgMap);
        redisTemplate.opsForValue().set(
                Constants.RABBITMQ_CORRELATION_MSG_ID + msgId,
                msgStrMap,
                Constants.MQ_RETRY_INTERVAL_TIME,
                TimeUnit.SECONDS);
        log.info("消息已存入Redis，msgId: {}", msgId);
        // 发送消息到评价消息队列
        rabbitTemplate.convertAndSend(
                "review.exchange", // 评价交换机
                "review.audit",
                message,
                msg -> {
                    msg.getMessageProperties().setMessageId(msgId);
                    return msg;
                },
                new CorrelationData(msgId));
        log.info("MQ消息发送成功，msgId: {}", msgId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewRatingRefresh(ReviewRatingRefreshEvent event) {
        // 构造 msgId
        String msgId = IdUtil.simpleUUID();
        // 存 Redis 重试元数据（跟 review.audit 一样可靠）
        Long restaurantId = event.getRestaurantId();
        ReviewRatingRefreshMessage message = new ReviewRatingRefreshMessage(restaurantId);
        // 消息体序列化
        String msgJson = JSONUtil.toJsonStr(message);
        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put(Constants.MQ_EXCHANGE, "review.exchange");
        msgMap.put(Constants.MQ_ROUTING_KEY, "review.rating.refresh");
        msgMap.put(Constants.MQ_MESSAGE, msgJson);
        msgMap.put(Constants.MQ_RETRY_COUNT, 0);
        // 把整个msgMap存储到redis还要把map转成Json String
        String cacheMap = JSONUtil.toJsonStr(msgMap);
        redisTemplate.opsForValue().set(
                Constants.RABBITMQ_CORRELATION_MSG_ID + msgId,
                cacheMap,
                Constants.MQ_RETRY_INTERVAL_TIME,
                TimeUnit.SECONDS);
        log.info("消息已存入Redis，msgId: {}", msgId);
        // 发送 review.exchange / review.rating.refresh
        // 设置 messageId
        // 发送消息到评价消息队列
        rabbitTemplate.convertAndSend(
                "review.exchange", // 评价交换机
                "review.rating.refresh",
                message,
                msg -> {
                    msg.getMessageProperties().setMessageId(msgId);
                    return msg;
                },
                new CorrelationData(msgId));
        log.info("MQ消息发送成功，msgId: {}", msgId);
    }

}
