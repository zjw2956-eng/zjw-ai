package cn.zjw.mq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.zjw.mq.message.ReviewRatingRefreshMessage;
import cn.zjw.mq.support.MqDedupService;
import cn.zjw.service.RatingUpdateService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReviewRatingUpdateConsumer {

    @Autowired
    private RatingUpdateService ratingUpdateService;

    @Autowired
    private MqDedupService mqDedupService;

    @RabbitListener(queues = "review.rating.refresh.queue")
    public void handleReviewRatingUpdate(ReviewRatingRefreshMessage message, Message rawMessage) {
        String msgId = mqDedupService.extractMessageId(rawMessage);
        if (!mqDedupService.tryMarkConsumed(msgId)) {
            log.info("重复消息，跳过，msgId={}", msgId);
            return;
        }
        Long restaurantId = message.getRestaurantId();
        try {
            log.info("收到更新评分消息，更新餐厅评分，餐厅ID：{}", restaurantId);
            // 调用更新餐厅评分的方法
            ratingUpdateService.updateRestaurantRating(restaurantId);
            log.info("更新餐厅评分完成，餐厅ID：{}", restaurantId);
        } catch (Exception e) {
            log.error("更新餐厅评分失败，餐厅ID：{}", restaurantId, e);
            mqDedupService.rollback(msgId);
            throw new RuntimeException("更新餐厅评分失败", e);
        }
    }

}
