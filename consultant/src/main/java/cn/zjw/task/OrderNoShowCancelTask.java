package cn.zjw.task;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.enums.OrderStatus;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import cn.zjw.service.OrderDelayService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderNoShowCancelTask {
    @Autowired
    private OrderDelayService orderDelayService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private CacheClient cacheClient;

    @Scheduled(fixedDelay = Constants.ORDER_NO_SHOW_SCAN_INTERVAL_MS)
    public void scanAndCancelTimeoutOrders() {
        long now = System.currentTimeMillis();
        Set<String> dueOrderNos = orderDelayService.pollDueOrders(
                now,
                Constants.ORDER_NO_SHOW_SCAN_BATCH_SIZE);
        if (dueOrderNos.isEmpty()) {
            return;
        }
        for (String orderNo : dueOrderNos) {
            String lockValue = orderDelayService.tryLock(orderNo);
            if (lockValue == null) {
                continue;
            }
            try {
                handleOneOrder(orderNo);
            } catch (Exception e) {
                log.error("处理超时未到店订单失败，orderNo={}", orderNo, e);
            } finally {
                orderDelayService.unlock(orderNo, lockValue);
            }
        }

    }

    private void handleOneOrder(String orderNo) {
        LambdaQueryWrapper<OrderInfo> query = new LambdaQueryWrapper<>();
        query.eq(OrderInfo::getOrderNo, orderNo).eq(OrderInfo::getIsDeleted, 0);
        OrderInfo order = orderMapper.selectOne(query);
        // 不存在直接清理
        if (order == null) {
            orderDelayService.removeFromDelayQueue(orderNo);
            return;
        }
        // 不是已确认状态，说明已完成/已取消，直接清理
        if (!order.getStatus().equals(OrderStatus.CONFIRMED.getCode())) {
            orderDelayService.removeFromDelayQueue(orderNo);
            return;
        }
        // 时间兜底校验（防止时钟漂移）
        LocalDateTime deadline = order.getReservationTime().plusMinutes(Constants.ORDER_NO_SHOW_GRACE_MINUTES);
        if (LocalDateTime.now().isBefore(deadline)) {
            return;
        }
        // 条件更新，防并发重复取消
        LambdaUpdateWrapper<OrderInfo> update = new LambdaUpdateWrapper<>();
        update.eq(OrderInfo::getOrderNo, orderNo)
                .eq(OrderInfo::getStatus, OrderStatus.CONFIRMED.getCode())
                .set(OrderInfo::getStatus, OrderStatus.CANCELLED.getCode());
        int updated = orderMapper.update(null, update);
        // 无论更新是否成功，都清理延时队列（成功=已取消；失败=大概率并发已变更）
        orderDelayService.removeFromDelayQueue(orderNo);
        if (updated > 0) {
            cacheClient.delete(Constants.REDIS_ORDER_DETAIL + order.getUserId() + ":" + orderNo);
            log.info("订单到店超时30分钟未消费，自动取消成功，orderNo={}", orderNo);
        } else {
            log.info("订单状态已变化，无需自动取消，orderNo={}", orderNo);
        }
    }
}
