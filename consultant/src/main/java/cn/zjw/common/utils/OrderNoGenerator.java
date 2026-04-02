package cn.zjw.common.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.zjw.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单号生成器（使用Redis原生SETNX实现分布式锁）
 */
@Slf4j
@Component
public class OrderNoGenerator {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 生成订单号
     */
    public String generateOrderNo() {
        String lockKey = Constants.REDIS_ORDER_NO_GENERATOR;
        String lockValue = String.valueOf(System.currentTimeMillis());

        try {
            // 尝试获取锁，最多等5秒
            for (int i = 0; i < 50; i++) {
                Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, 500, TimeUnit.MILLISECONDS);

                if (Boolean.TRUE.equals(locked)) {
                    try {
                        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                        String redisKey = Constants.REDIS_ORDER_NO_SEQ + today;

                        Long seq = stringRedisTemplate.opsForValue().increment(redisKey);
                        stringRedisTemplate.expire(redisKey, 2, TimeUnit.DAYS);

                        return Constants.ORDER_ID_PREFIX + today + String.format("%010d", seq);
                    } finally {
                        stringRedisTemplate.delete(lockKey);
                    }
                }

                Thread.sleep(100);
            }

            throw new RuntimeException("获取订单号生成锁超时");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取订单号生成锁被中断", e);
            throw new RuntimeException("订单号生成失败：锁获取中断", e);
        } catch (Exception e) {
            log.error("生成订单号失败：{}", e.getMessage(), e);
            throw new RuntimeException("订单号生成失败", e);
        }
    }
}
