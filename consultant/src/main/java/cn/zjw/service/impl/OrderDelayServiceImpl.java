package cn.zjw.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import cn.zjw.common.constant.Constants;
import cn.zjw.service.OrderDelayService;


@Service
public class OrderDelayServiceImpl implements OrderDelayService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1] == ARGV[1] then " +
                        " return redis.call('del',KEYS[1])" +
                        "else return 0 end");
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void enqueueNoShowCancel(String orderNo, LocalDateTime reservationTime) {
        long triggerAt = reservationTime
                .plusMinutes(Constants.ORDER_NO_SHOW_GRACE_MINUTES)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        stringRedisTemplate.opsForZSet().add(
                Constants.ORDER_NO_SHOW_DELAY_ZSET_KEY,
                orderNo,
                triggerAt);
    }

    @Override
    public Set<String> pollDueOrders(long nowEpochMillis, int limit) {
        Set<String> due = stringRedisTemplate.opsForZSet()
                .rangeByScore(Constants.ORDER_NO_SHOW_DELAY_ZSET_KEY, 0, nowEpochMillis, 0, limit);
        return due == null ? Collections.emptySet() : due;
    }

    @Override
    public String tryLock(String orderNo) {
        String lockKey = Constants.ORDER_NO_SHOW_LOCK_KEY_PREFIX + orderNo;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Constants.ORDER_NO_SHOW_LOCK_SECONDS,
                TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(locked)){
            return null;
        }
        return lockValue;
        
    }

    @Override
    public void unlock(String orderNo,String lockValue) {
        if(lockValue == null) return;
        String lockKey = Constants.ORDER_NO_SHOW_LOCK_KEY_PREFIX +  orderNo;
        stringRedisTemplate.execute(
            UNLOCK_SCRIPT,
            Collections.singletonList(lockKey),
            lockValue
        );
    }

    @Override
    public void removeFromDelayQueue(String orderNo) {
        stringRedisTemplate.opsForZSet().remove(Constants.ORDER_NO_SHOW_DELAY_ZSET_KEY, orderNo);
    }

}
