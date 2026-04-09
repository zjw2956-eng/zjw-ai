package cn.zjw.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;

@Slf4j
@Configuration
public class RabbitMQConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    RabbitMQConfig(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 配置消息转换器，将Java对象转换为JSON格式
     * 
     * @return
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());

        // 发送消息确认
        // 1. 必须开启 mandatory，否则 ReturnsCallback 不会触发
        rabbitTemplate.setMandatory(true);
        // 2.第一个回调：ConfirmCallback(确认消息发送成功，交换机确认)
        // 交换机确认消息后，会调用该回调
        // 可以在回调中更新数据库，标记消息已处理
        // 消息没到达交换机（网络或 Broker 问题）
        rabbitTemplate.setConfirmCallback(
                (correlationData, // （消息标识）
                        ack, // （是否成功）
                        cause// （失败原因）
                ) -> {
                    String msgId = correlationData != null ? correlationData.getId() : null;
                    if (msgId == null) {
                        log.warn("ConfirmCallback收到无ID消息，跳过处理");
                        return;
                    }
                    // 如果ack为true，说明消息发送成功
                    // 如果ack为false，说明消息发送失败
                    if (ack) {
                        log.info("消息发送到交换机成功，msgId: {}，等待路由队列确认", msgId);
                        delayDeleteCacheAfterConfirm(msgId);
                    } else {
                        log.error("消息发送到交换机失败，msgId: {}，原因: {}", msgId, cause);
                        handleMessageRetry(msgId, rabbitTemplate);
                    }
                });
        // 3.第二个回调：ReturnsCallback(路由失败回退)
        // 消息到了交换机，但路由不到队列（routing key 写错了等）
        rabbitTemplate.setReturnsCallback(
                (returned // （RabbitMQ自动封装的返回的消息类）
                ) -> {
                    Message message = returned.getMessage();
                    String msgId = message.getMessageProperties().getHeader("spring_returned_message_correlation");
                    if (msgId == null) {
                        log.warn("ReturnsCallback收到无ID消息，跳过处理");
                        return;
                    }
                    String exchange = returned.getExchange();
                    String routingKey = returned.getRoutingKey();
                    int replyCode = returned.getReplyCode();
                    String replyText = returned.getReplyText();

                    log.error("消息路由到队列失败，msgId: {}，交换机：{}，路由键：{}，回复码：{}，回复文本：{}",
                            msgId, exchange, routingKey, replyCode, replyText);
                    markRouteFailed(msgId);
                    handleMessageRetry(msgId, rabbitTemplate);
                });
        return rabbitTemplate;
    }

    /**
     * 交换机确认成功后延迟删除缓存：若短时间内发生路由失败(ReturnsCallback)，则保留缓存用于重试
     */
    private void delayDeleteCacheAfterConfirm(String msgId) {
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            String routeFailedKey = Constants.RABBITMQ_CORRELATION_MSG_ID + msgId + ":route_failed";
            Boolean routeFailed = redisTemplate.hasKey(routeFailedKey);
            if (Boolean.TRUE.equals(routeFailed)) {
                log.info("检测到路由失败标记，暂不删除缓存，msgId: {}", msgId);
                return;
            }
            redisTemplate.delete(Constants.RABBITMQ_CORRELATION_MSG_ID + msgId);
            log.info("消息发送并路由成功，删除Redis缓存，msgId: {}", msgId);
        });
    }

    /**
     * 标记路由失败，供延迟删除逻辑判断
     */
    private void markRouteFailed(String msgId) {
        String routeFailedKey = Constants.RABBITMQ_CORRELATION_MSG_ID + msgId + ":route_failed";
        redisTemplate.opsForValue().set(routeFailedKey, "1", Constants.MQ_RETRY_INTERVAL_TIME, TimeUnit.SECONDS);
    }

    /**
     * 处理消息重试
     * 
     * @param msgId   消息ID
     * @param string  缓存键
     * @param string2 回调类型
     */
    private void handleMessageRetry(String msgId, RabbitTemplate rabbitTemplate) {
        String redisKey = Constants.RABBITMQ_CORRELATION_MSG_ID + msgId;
        // 1.从redis中取出消息
        String msgStr = (String) redisTemplate.opsForValue().get(redisKey);
        if (msgStr == null) {
            log.warn("handleMessageRetry收到无消息体的消息，跳过处理");
            return;
        }
        Map<String, Object> msgMap = JSONUtil.toBean(msgStr, Map.class);
        String exchange = (String) msgMap.get("exchange");
        String routingKey = (String) msgMap.get("routingKey");
        // 从消息体中获取消息体
        String msgJson = (String) msgMap.get("message");
        // 从消息体中获取重试次数
        Integer retryCount = (Integer) msgMap.get("retryCount");
        if (exchange == null || routingKey == null || msgJson == null || retryCount == null) {
            log.error("handleMessageRetry 消息缓存数据不完整，msgId: {}", msgId);
            return;
        }
        // 判断重试次数
        if (retryCount > Constants.MAX_RETRY_COUNT) {
            log.error("消息重试次数超过最大限制，转入失败缓存，msgId: {}", msgId);
            redisTemplate.opsForValue().set(
                    Constants.MQ_FAILED_RETRY_KEY + msgId,
                    msgJson,
                    Constants.MQ_FAILED_RETRY_EXPIRE_TIME,
                    TimeUnit.SECONDS);
            redisTemplate.delete(redisKey);
            redisTemplate.delete(Constants.RABBITMQ_CORRELATION_MSG_ID + msgId + ":route_failed");
            log.info("handleMessageRetry删除Redis缓存成功，msgId: {}", msgId);
            return;
        }
        // 4. 重试次数+1，更新缓存
        msgMap.put("retryCount", retryCount + 1);
        redisTemplate.opsForValue().set(
                redisKey,
                JSONUtil.toJsonStr(msgMap),
                Constants.MQ_RETRY_INTERVAL_TIME,
                TimeUnit.SECONDS);
        // 5. 重新发送消息（反序列化为 Object，让 Jackson 自动处理）
        try {
            Object retryMsg = JSONUtil.parse(msgJson);
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    retryMsg,
                    message -> {
                        message.getMessageProperties().setMessageId(msgId);
                        return message;
                    },
                    new CorrelationData(msgId));
            log.info("消息重试发送成功，msgId: {}，重试次数: {}", msgId, retryCount + 1);
        } catch (Exception e) {
            log.error("消息重试发送失败，msgId: {}", msgId, e);
        }

    }

    // ==================== 评价审核相关配置 ====================

    // 死信交换机
    @Bean
    public DirectExchange reviewDlxExchange() {
        return new DirectExchange("review.dlx.exchange");
    }

    // 死信队列（存放处理失败的消息）
    @Bean
    public Queue reviewAuditDlxQueue() {
        return QueueBuilder.durable("review.audit.dlx.queue").build();
    }

    // 绑定死信队列到死信交换机
    @Bean
    public Binding reviewAuditDlxBinding() {
        return BindingBuilder
                .bind(reviewAuditDlxQueue())
                .to(reviewDlxExchange())
                .with("review.audit.dlx");
    }

    // 定义交换机
    @Bean
    public DirectExchange reviewExchange() {
        return new DirectExchange("review.exchange");
    }

    // 定义审核队列
    @Bean
    public Queue reviewAuditQueue() {
        return QueueBuilder.durable("review.audit.queue")
                .withArgument("x-dead-letter-exchange", "review.dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "review.audit.dlx")
                .build();
    }

    // 綁定隊列到交換機
    @Bean
    public Binding reviewAuditBinding() {
        return BindingBuilder
                .bind(reviewAuditQueue())
                .to(reviewExchange())
                .with("review.audit"); // 路由鍵
    }

    // 评价通过队列
    @Bean
    public Queue reviewRatingRefreshQueue() {
        return QueueBuilder.durable("review.rating.refresh.queue").build();
    }

    /**
     * 绑定评价通过队列到交换机
     */
    @Bean
    public Binding reviewRatingRefreshBinding() {
        return BindingBuilder
                .bind(reviewRatingRefreshQueue())
                .to(reviewExchange())
                .with("review.rating.refresh");
    }

    // ==================== 订单确认超时相关配置 ====================

    /**
     * 订单死信交换机（用于接收过期的延迟消息）
     */
    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange("order.dlx.exchange");
    }

    /**
     * 订单确认超时延迟队列
     * - 30分钟TTL
     * - 无消费者监听
     * - 消息过期后转发到死信交换机
     */
    @Bean
    public Queue orderConfirmTimeoutDelayQueue() {
        return QueueBuilder.durable("order.confirm.timeout.delay.queue")
                .withArgument("x-message-ttl", Constants.MQ_DELAY_EXPIRE * 60 * 1000) // 30分钟
                .withArgument("x-dead-letter-exchange", "order.dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "order.confirm.timeout")
                .build();
    }

    /**
     * 订单确认超时实际消费队列（绑定到死信交换机）
     * 这个队列其实就是死信队列，等到order.confirm.timeout.delay.queue中的消息在30分钟后过期，
     * 过期的消息就会被转发到死信交换机，死信交换机通过路由键order.confirm.timeout
     * 把过期的消息转发到当前队列orderConfirmTimeoutQueue，
     * 由OrderConfirmTimeoutConsumer类中的handleOrderConfirmTimeout监听当前队列，
     * 收到过期消息后进行业务处理
     */
    @Bean
    public Queue orderConfirmTimeoutQueue() {
        return QueueBuilder.durable("order.confirm.timeout.queue").build();
    }

    /**
     * 绑定消费队列到死信交换机
     */
    @Bean
    public Binding orderConfirmTimeoutBinding() {
        return BindingBuilder
                .bind(orderConfirmTimeoutQueue())
                .to(orderDlxExchange())
                .with("order.confirm.timeout");
    }

    // ==================== 数据同步到ES相关配置 ====================
    // 同步ES数据交换机
    @Bean
    public DirectExchange RestaurantEsSyncExchange() {
        return new DirectExchange("restaurant.es.sync.exchange");
    }

    // 定义同步ES数据队列
    @Bean
    public Queue RestaurantEsSyncQueue() {
        return QueueBuilder.durable("restaurant.es.sync.queue")
                .build();
    }

    /**
     * 绑定消费队列到死信交换机
     */
    @Bean
    public Binding EsSyncBinding() {
        return BindingBuilder
                .bind(RestaurantEsSyncQueue())
                .to(RestaurantEsSyncExchange())
                .with("restaurant.es.sync");
    }

}
