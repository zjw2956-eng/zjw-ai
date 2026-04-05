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

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.mq.event.OrderCreatedEvent;
import cn.zjw.mq.message.OrderConfirmTimeoutMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderEventListener {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 监听订单创建事件，发送确认超时延迟消息
     * - 在事务提交后触发
     * - 保证订单已成功入库
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("订单创建事务提交，发送MQ消息...此消息没有消费者...30分钟后过期，进入死信交换机转发到死信队列的消费者进行处理...");
        String orderNo = event.getOrderNo();
        try {
            OrderConfirmTimeoutMessage message = new OrderConfirmTimeoutMessage(orderNo);
            // 生成消息ID
            String msgId = IdUtil.simpleUUID();
            // 缓存消息到Redis（包含路由信息）
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put(Constants.MQ_EXCHANGE, ""); // 默认交换机
            msgMap.put(Constants.MQ_ROUTING_KEY, "order.confirm.timeout.delay.queue");
            msgMap.put(Constants.MQ_MESSAGE, JSONUtil.toJsonStr(message));
            msgMap.put(Constants.MQ_RETRY_COUNT, 0);
            redisTemplate.opsForValue().set(
                    Constants.RABBITMQ_CORRELATION_MSG_ID + msgId,
                    JSONUtil.toJsonStr(msgMap),
                    Constants.MQ_DELAY_CACHE_EXPIRE, // 31分钟，比延迟时间多1分钟
                    TimeUnit.MINUTES);
            log.info("消息已存入Redis，msgId: {}", msgId);
            // 发送消息到延迟队列
            /**
             * 使用默认交换机的时候，路由键就是要转发的队列名称
             */
            rabbitTemplate.convertAndSend(
                    "", // 默认交换机
                    "order.confirm.timeout.delay.queue",
                    message,
                    msg -> {
                        msg.getMessageProperties().setMessageId(msgId);
                        return msg;
                    },
                    new CorrelationData(msgId));
            log.info("订单确认超时延迟消息发送成功，orderNo={}, msgId={}", orderNo, msgId);
        } catch (Exception e) {
            log.error("订单确认超时延迟消息发送失败，orderNo={}", orderNo, e);
            // 注意：这里不抛异常，避免影响事务
            // 消息发送失败会通过 ConfirmCallback 重试
        }

    }
}
