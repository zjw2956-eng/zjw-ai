package cn.zjw.mq.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.zjw.common.enums.OrderStatus;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.mq.message.OrderConfirmTimeoutMessage;
import cn.zjw.pojo.entity.OrderInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单确认超时消费者
 */
@Component
@Slf4j
public class OrderConfirmTimeoutConsumer {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理订单确认超时消息，这个消费者其实就是死信队列消费者
     * - 检查订单状态是否还是 PENDING
     * - 如果是，自动取消订单
     * - 如果不是，说明已经确认或取消，不处理（幂等）
     */
    @RabbitListener(queues = "order.confirm.timeout.queue")
    public void handleOrderConfirmTimeout(OrderConfirmTimeoutMessage message) {
        try {
            log.info("收到订单确认超时消息: {}", message);
            LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OrderInfo::getOrderNo, message.getOrderNo());
            OrderInfo order = orderMapper.selectOne(wrapper);
            // 2.幂等检查：订单不存在
            if (order == null) {
                log.warn("订单不存在，跳过处理，orderNo={}", message.getOrderNo());
                return;
            }
            if (!order.getStatus().equals(OrderStatus.PENDING.getCode())) {
                log.info("订单状态已变更，无需取消，orderNo={}, status={}",
                        message.getOrderNo(), order.getStatus());
                return;
            }
            // 更新订单状态为已取消
            order.setStatus(OrderStatus.CANCELLED.getCode());
            int updated = orderMapper.updateById(order);
            if (updated > 0) {
                log.info("订单确认超时自动取消成功，orderNo={}", message.getOrderNo());
                // TODO: 这里可以发送通知给用户（短信/推送）
                // notificationService.sendOrderCancelNotice(order.getUserId(),
                // order.getOrderNo());
            } else {
                log.error("订单确认超时取消失败，数据库更新返回0，orderNo={}", message.getOrderNo());
            }
        } catch (Exception e) {
            log.error("订单确认超时处理失败，orderNo={}", message.getOrderNo(), e);
            // 抛出异常，触发 RabbitMQ 重试机制
            throw new RuntimeException("订单确认超时处理失败", e);
        }
    }

}
