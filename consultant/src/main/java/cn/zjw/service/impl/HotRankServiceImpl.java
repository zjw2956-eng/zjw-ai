package cn.zjw.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.dto.HotRankItem;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.HotRankService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HotRankServiceImpl implements HotRankService {

    private static final int DEFAULT_LIMIT = 10;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void refreshHotRestaurantRank() {
        String lockKey = Constants.REDIS_LOCK_RESTAURANT_HOT_RANK_KEY;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(
                    Constants.LOCK_GET_TIME,
                    Constants.LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS); // 抢锁：不等待，拿不到立即返回 false，持有10秒
            if (!locked) {
                // tryLock 获取不到直接返回（说明已有刷新在进行），不需要等待
                return;
            }
            // 先取出旧榜单ID（用于删除旧详情缓存）
            Set<String> oldRankIds = stringRedisTemplate.opsForZSet()
                    .range(Constants.HOT_RESTAURANT_RANK_KEY, 0, -1);
            // 删除旧榜单Zset
            stringRedisTemplate.delete(Constants.HOT_RESTAURANT_RANK_KEY);
            // 删除旧榜单详情缓存
            if (oldRankIds != null && !oldRankIds.isEmpty()) {
                List<String> oldDetailKeys = oldRankIds.stream()
                        .map(id -> Constants.HOT_RESTAURANT_DETAIL_KEY + id)
                        .collect(Collectors.toList());
                stringRedisTemplate.delete(oldDetailKeys);
                log.info("已删除旧热门榜单详情缓存 {} 条", oldDetailKeys.size());
            }

            // 查询30天内已完成订单聚合
            List<HotRankItem> rankItems = orderMapper.selectHotRankLast30Days();
            if (rankItems == null || rankItems.isEmpty()) {
                log.info("热门榜单刷新完成，但无数据");
                return;
            }
            // 批量写入 ZSet
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (HotRankItem item : rankItems) {
                if (item.getRestaurantId() == null || item.getScore() == null) {
                    continue;
                }
                tuples.add(ZSetOperations.TypedTuple.of(
                        String.valueOf(item.getRestaurantId()),
                        item.getScore().doubleValue()));
            }
            if (!tuples.isEmpty()) {
                stringRedisTemplate.opsForZSet().add(
                        Constants.HOT_RESTAURANT_RANK_KEY,
                        tuples);
            }
            // 4. 预热热门餐厅详情缓存
            preheatHotRestaurantDetails(Constants.HOT_RESTAURANT_PREHEAT_LIMIT);
            log.info("热门榜单刷新完成，写入 {} 条", tuples.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("刷新热榜被中断", e);
        } catch (RuntimeException e) {
            throw e;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    @Override
    public List<Long> getHotRestaurantIds(int limit) {
        int size = limit > 0 ? limit : DEFAULT_LIMIT;
        Set<String> idSet = stringRedisTemplate.opsForZSet()
                .reverseRange(Constants.HOT_RESTAURANT_RANK_KEY, 0, size - 1);
        if (idSet == null || idSet.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(idSet.size());
        for (String idStr : idSet) {
            ids.add(Long.valueOf(idStr));
        }
        return ids;
    }

    @Override
    public List<RestaurantVO> getHotRestaurants(int limit) {
        List<Long> ids = getHotRestaurantIds(limit);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查询缓存
        List<String> detailKeys = ids.stream()
                .map(id -> Constants.HOT_RESTAURANT_DETAIL_KEY + id)
                .collect(Collectors.toList());

        // 取出来的是Json格式的String类型值，是RestaurantVO
        List<String> cacheJsonList = stringRedisTemplate.opsForValue().multiGet(detailKeys);
        Map<Long, RestaurantVO> voMap = new HashMap<>();

        List<Long> missIds = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            if (cacheJsonList == null) {
                return null;
            }
            String json = cacheJsonList.get(i);
            // json为空说明，当前redis缓存里面没有这个餐厅id对应的vo
            if (json == null || json.isBlank()) {
                missIds.add(id);
                continue;
            }
            // json不为空。把json字符串转换成RestaurantVO，再存入voMap
            try {
                RestaurantVO vo = JSONUtil.toBean(json, RestaurantVO.class);
                voMap.put(id, vo);
            } catch (Exception e) {
                // 解析失败按 miss 处理
                missIds.add(id);
            }
        }
        // missIds中的id回源DB，再写入缓存
        if (!missIds.isEmpty()) {
            List<Restaurant> restaurants = restaurantMapper.selectBatchIds(missIds);
            if (restaurants != null && !restaurants.isEmpty()) {
                // 批量写入缓存的 Map
                Map<String, String> cacheMap = new HashMap<>();

                for (Restaurant r : restaurants) {
                    RestaurantVO vo = new RestaurantVO();
                    BeanUtil.copyProperties(r, vo);
                    voMap.put(r.getId(), vo);

                    // 准备批量写入的数据
                    String key = Constants.HOT_RESTAURANT_DETAIL_KEY + r.getId();
                    cacheMap.put(key, JSONUtil.toJsonStr(vo));
                }

                // 批量写入 Redis（一次网络 IO）
                if (!cacheMap.isEmpty()) {
                    stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                        cacheMap.forEach((key, value) -> {
                            connection.stringCommands().set(
                                    key.getBytes(StandardCharsets.UTF_8),
                                    value.getBytes(StandardCharsets.UTF_8),
                                    Expiration.from(Constants.HOT_RESTAURANT_DETAIL_TTL_DAYS, TimeUnit.DAYS),
                                    SetOption.UPSERT);
                        });
                        return null; // Pipeline要求返回null
                    });
                }

            }
        }
        // 按榜单顺序重排序返回
        List<RestaurantVO> result = new ArrayList<>();
        for (Long id : ids) {
            RestaurantVO vo = voMap.get(id);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * 预热 TopN 餐厅详情缓存
     */
    private void preheatHotRestaurantDetails(int topN) {
        List<Long> hotIds = getHotRestaurantIds(topN);
        if (hotIds.isEmpty()) {
            return;
        }
        List<Restaurant> restaurants = restaurantMapper.selectBatchIds(hotIds);
        if (restaurants == null || restaurants.isEmpty()) {
            return;
        }

        // 批量写入缓存的 Map
        Map<String, String> cacheMap = new HashMap<>();
        for (Restaurant r : restaurants) {
            RestaurantVO vo = new RestaurantVO();
            BeanUtil.copyProperties(r, vo);
            String key = Constants.HOT_RESTAURANT_DETAIL_KEY + r.getId();
            cacheMap.put(key, JSONUtil.toJsonStr(vo));
        }

        // 批量写入 Redis（一次网络 IO）
        // 替换这段（multiSet + 注释掉的循环expire）
        if (!cacheMap.isEmpty()) {
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                cacheMap.forEach((key, value) -> {
                    connection.stringCommands().set(
                            key.getBytes(StandardCharsets.UTF_8),
                            value.getBytes(StandardCharsets.UTF_8),
                            Expiration.from(Constants.HOT_RESTAURANT_DETAIL_TTL_DAYS, TimeUnit.DAYS),
                            SetOption.UPSERT);
                });
                return null; // Pipeline要求返回null
            });
        }
    }

}
