package cn.zjw.common.cache;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
@Component
@Slf4j
public class CacheClient {

    /**
     * 空值哨兵（防穿透），必须是固定 raw string，不走 JSON 序列化
     */
    private static final String NULL_SENTINEL = "__NULL__";

    /**
     * 逻辑过期方案专用线程池，用于异步重建缓存
     */
    private static final ExecutorService REBUILD_EXECUTOR =
            Executors.newFixedThreadPool(Constants.LOGICAL_EXPIRE_THREAD_POOL_SIZE);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 通用写缓存
     */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), ttl, unit);
    }

    /**
     * 写缓存+随机抖动TTL防雪崩
     */
    public void setWithRandomTtl(String key, Object value, long baseSeconds, int randomSeconds, TimeUnit unit) {
        long finalTtl = withRandomTtl(baseSeconds, randomSeconds);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), finalTtl, unit);
    }

    /**
     * 写空值哨兵（raw string，不走 JSON）
     */
    public void setNullValue(String key, long baseSeconds, int randomSeconds, TimeUnit unit) {
        long finalTtl = withRandomTtl(baseSeconds, randomSeconds);
        stringRedisTemplate.opsForValue().set(key, NULL_SENTINEL, finalTtl, unit);
    }

    /**
     * 通用读缓存
     * 
     * @param key  缓存键
     * @param type 缓存值类型
     * @return 缓存值
     */
    public <R> R get(String key, Class<R> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank() || NULL_SENTINEL.equals(json)) {
            return null;
        }
        return JSONUtil.toBean(json, type);
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 生成随机 TTL，用于防雪崩
     */
    private long withRandomTtl(long baseSeconds, int randomSeconds) {
        if (randomSeconds <= 0) {
            return baseSeconds;
        }
        int extra = ThreadLocalRandom.current().nextInt(randomSeconds);
        return baseSeconds + extra;
    }

    private void count(String metric, String biz, String strategy, String result) {
        Counter.builder(metric)
                .tag("biz", biz)
                .tag("strategy", strategy)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private void recordLatency(String biz, String strategy, long startNanos) {
        Timer.builder("cache.latency")
                .tag("biz", biz)
                .tag("strategy", strategy)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 防穿透：缓存命中 -> 返回；空值命中 -> 返回null；未命中 -> 查DB回填
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            long cacheTtlSeconds,
            long emptyTtlSeconds,
            String biz) {
        String strategy = "pass_through";
        long startNanos = System.nanoTime();
        count("cache.request.total", biz, strategy, "all");

        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //  命中空值哨兵
        if (NULL_SENTINEL.equals(json)) {
            count("cache.hits.total", biz, strategy, "empty_hit");
            recordLatency(biz, strategy, startNanos);
            return null;
        }
        //  缓存命中（正常值）
        if (json != null && !json.isBlank() && !NULL_SENTINEL.equals(json)) {
            count("cache.hits.total", biz, strategy, "hit");
            recordLatency(biz, strategy, startNanos);
            return JSONUtil.toBean(json, type);
        }

        // 3) 缓存未命中
        count("cache.misses.total", biz, strategy, "miss");
        count("cache.db.fallback.total", biz, strategy, "fallback");
        try {
            R result = dbFallback.apply(id);
            if (result == null) {
                // 写空值哨兵防穿透
                setNullValue(key, emptyTtlSeconds, Constants.RANDOM_SECONDS, TimeUnit.SECONDS);
                count("cache.write.total", biz, strategy, "null_write");
                return null;
            }
            // 写正常值
            setWithRandomTtl(key, result, cacheTtlSeconds, Constants.RANDOM_SECONDS, TimeUnit.SECONDS);
            count("cache.write.total", biz, strategy, "value_write");
            return result;
        } catch (RuntimeException e) {
            count("cache.errors.total", biz, strategy, "exception");
            throw e;
        } finally {
            recordLatency(biz, strategy, startNanos);
        }

    }

    /**
     * 防击穿：在 pass-through 基础上增加互斥锁重建缓存
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            String lockPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            long cacheTtlSeconds,
            long emptyTtlSeconds,
            String biz) {
        String strategy = "mutex";
        long startNanos = System.nanoTime();
        count("cache.request.total", biz, strategy, "all");

        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 1) 缓存命中（正常值）
        if (json != null && !json.isBlank() && !NULL_SENTINEL.equals(json)) {
            count("cache.hits.total", biz, strategy, "hit");
            recordLatency(biz, strategy, startNanos);
            return JSONUtil.toBean(json, type);
        }
        // 2) 命中空值哨兵
        if (NULL_SENTINEL.equals(json)) {
            count("cache.hits.total", biz, strategy, "empty_hit");
            recordLatency(biz, strategy, startNanos);
            return null;
        }

        // 3) 缓存未命中
        count("cache.misses.total", biz, strategy, "miss");
        String lockKey = lockPrefix + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            // 抢锁：不等待，拿不到立即返回 false
            locked = lock.tryLock(
                    Constants.LOCK_GET_TIME,
                    Constants.LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS);
            if (!locked) {
                count("cache.lock.total", biz, strategy, "failed");
                Thread.sleep(Constants.LOCK_RETRY_SLEEP_MILLIS);
                // 重试读缓存
                String retryJson = stringRedisTemplate.opsForValue().get(key);
                if (NULL_SENTINEL.equals(retryJson)) {
                    count("cache.hits.total", biz, strategy, "retry_empty_hit");
                    return null;
                }
                if (retryJson != null && !retryJson.isBlank() && !NULL_SENTINEL.equals(retryJson)) {
                    count("cache.hits.total", biz, strategy, "retry_hit");
                    return JSONUtil.toBean(retryJson, type);
                }

                // // 兜底一次回源（避免大量线程同时打 DB）
                // count("cache.db.fallback.total", biz, strategy, "fallback_after_lock_fail");
                // R retryResult = dbFallback.apply(id);
                // if (retryResult == null) {
                // setNullValue(key, emptyTtlSeconds, Constants.RANDOM_SECONDS,
                // TimeUnit.SECONDS);
                // count("cache.write.total", biz, strategy, "null_write");
                // return null;
                // }
                // setWithRandomTtl(key, retryResult, cacheTtlSeconds, Constants.RANDOM_SECONDS,
                // TimeUnit.SECONDS);
                // count("cache.write.total", biz, strategy, "value_write");

                // 缓存还没建好，返回 null 或抛异常，让调用方处理
                // 不要在这里回源！
                return null;
            }
            count("cache.lock.total", biz, strategy, "acquired");
            // Double check（拿到锁后再查一次缓存）
            String doubleCheck = stringRedisTemplate.opsForValue().get(key);
            if (NULL_SENTINEL.equals(doubleCheck)) {
                count("cache.hits.total", biz, strategy, "double_check_empty_hit");
                return null;
            }
            if (doubleCheck != null && !doubleCheck.isBlank() && !NULL_SENTINEL.equals(doubleCheck)) {
                count("cache.hits.total", biz, strategy, "double_check_hit");
                return JSONUtil.toBean(doubleCheck, type);
            }

            // 真正回源
            count("cache.db.fallback.total", biz, strategy, "fallback_after_lock");
            R result = dbFallback.apply(id);
            if (result == null) {
                setNullValue(key, emptyTtlSeconds, Constants.RANDOM_SECONDS, TimeUnit.SECONDS);
                count("cache.write.total", biz, strategy, "null_write");
                return null;
            }
            setWithRandomTtl(key, result, cacheTtlSeconds, Constants.RANDOM_SECONDS, TimeUnit.SECONDS);
            count("cache.write.total", biz, strategy, "value_write");
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            count("cache.errors.total", biz, strategy, "interrupted");
            throw new RuntimeException("缓存重建被中断", e);
        } catch (RuntimeException e) {
            count("cache.errors.total", biz, strategy, "exception");
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            recordLatency(biz, strategy, startNanos);
        }

    }

    /**
     * 写入逻辑过期缓存（物理TTL设为7天，逻辑过期时间存在数据里）
     */
    public void setWithLogicalExpire(String key, Object value, long logicalTtlSeconds) {
        CacheData<Object> cacheData = new CacheData<>(value,
                LocalDateTime.now().plusSeconds(logicalTtlSeconds));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(cacheData),
                Constants.LOGICAL_EXPIRE_PHYSICAL_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 逻辑过期方案：读缓存
     * - 未命中：返回 null（需提前预热）
     * - 命中未过期：直接返回
     * - 命中已过期：返回旧值 + 异步重建缓存
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix,
            String lockPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            long logicalTtlSeconds,
            String biz) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 1) 缓存未命中（冷数据，需预热后才能用此方案）
        if (json == null || json.isBlank()) {
            log.warn("[逻辑过期] 缓存未命中，key={}，请检查是否已预热", key);
            return null;
        }

        // 2) 反序列化包装对象
        CacheData<Object> cacheData = JSONUtil.toBean(json,
                new TypeReference<CacheData<Object>>() {}, false);
        // hutool 泛型擦除，data 实际是 JSONObject，需要再转一次
        R data = JSONUtil.toBean(JSONUtil.toJsonStr(cacheData.getData()), type);
        LocalDateTime expireTime = cacheData.getExpireTime();

        // 3) 未过期，直接返回
        if (LocalDateTime.now().isBefore(expireTime)) {
            return data;
        }

        // 4) 已过期，尝试抢锁异步重建
        String lockKey = lockPrefix + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = lock.tryLock();
        if (locked) {
            // 抢到锁，提交异步重建任务
            REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newData = dbFallback.apply(id);
                    if (newData != null) {
                        setWithLogicalExpire(key, newData, logicalTtlSeconds);
                        log.info("[逻辑过期] 缓存重建完成，key={}", key);
                    }
                } catch (Exception e) {
                    log.error("[逻辑过期] 缓存重建失败，key={}", key, e);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });
        }
        // 无论是否抢到锁，都立刻返回旧数据，不阻塞用户
        return data;
    }
}
