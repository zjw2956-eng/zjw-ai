package cn.zjw.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
/**
 * 订单号生成器
 */
import org.springframework.stereotype.Component;

import cn.zjw.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderNoGenerator {

    /**
     * 生成订单号
     */
    public String generateOrderNo(RedissonClient redissonClient,StringRedisTemplate stringRedisTemplate) {
        RLock lock = redissonClient.getLock(Constants.REDIS_ORDER_NO_GENERATOR);
        try {
            // 先尝试获取锁，避免阻塞，最多等5秒，持有3秒
            boolean acquired = lock.tryLock(5, 3, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("获取订单号生成器锁超时");
            }
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String redisKey = Constants.REDIS_ORDER_NO_SEQ + today;
            /**
             * Redis原子操作：
             * 1. 检查key是否存在
             * - 如果不存在 → 自动创建，初始值为0
             * - 如果存在 → 读取当前值
             * 2. 自增+1
             * - 当前值 +1
             * 3. 返回自增后的值
             * - 返回给你的 seq 就是自增后的结果
             */
            Long seq = stringRedisTemplate.opsForValue().increment(redisKey);
            stringRedisTemplate.expire(redisKey, 2, TimeUnit.DAYS);
            
            return Constants.ORDER_ID_PREFIX + today + String.format("%010d", seq);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            log.error("获取订单号生成锁被中断", e);
            throw new RuntimeException("订单号生成失败：锁获取中断", e);
        } catch (Exception e) {
            log.error("生成订单号失败：{}", e.getMessage(), e);
            throw new RuntimeException("订单号生成失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                log.info("释放订单号生成器锁");
                lock.unlock();// 释放锁
            }
        }
    }
}
