package cn.zjw.common.utils;

import cn.zjw.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 订单号生成器（基于 Redis 原子自增）
 */
@Slf4j
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成订单号：ORDER + yyyyMMdd + 10位序号
     */
    public String generateOrderNo() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String redisKey = Constants.REDIS_ORDER_NO_SEQ + today;

        try {
            Long seq = stringRedisTemplate.opsForValue().increment(redisKey);
            if (seq == null) {
                throw new RuntimeException("订单号序列生成失败");
            }

            // 仅在当天首次自增时设置过期，减少重复 expire 开销
            if (seq == 1L) {
                stringRedisTemplate.expire(redisKey, 2, TimeUnit.DAYS);
            }

            return Constants.ORDER_ID_PREFIX + today + String.format("%010d", seq);
        } catch (Exception e) {
            log.error("生成订单号失败", e);
            throw new RuntimeException("订单号生成失败", e);
        }
    }
}
