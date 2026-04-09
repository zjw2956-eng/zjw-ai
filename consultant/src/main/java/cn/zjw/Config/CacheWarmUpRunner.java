package cn.zjw.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.zjw.service.HotRankService;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.RestaurantService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;

/**
 * 缓存预热：应用启动时预热热门数据
 */
@Slf4j
@Component
public class CacheWarmUpRunner implements ApplicationRunner {

    private static final String INIT_KEY = "Cache:IsWarmUp";
    private static final Duration TTL = Duration.ofDays(300);  // 300天过期

    @Autowired
    private HotRankService hotRankService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(INIT_KEY))){
            log.info("知识库已初始化，跳过加载");
            return;
        }
        log.info("========== 开始缓存预热 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 预热热门榜单（会自动预热 Top 20 餐厅详情）
            log.info("预热热门榜单...");
            hotRankService.refreshHotRestaurantRank();

            // 2. 预热 Top 30 餐厅详情（确保缓存充足）
            log.info("预热热门餐厅详情...");
            List<RestaurantVO> topRestaurants = hotRankService.getHotRestaurants(30);
            for (RestaurantVO vo : topRestaurants) {
                // 触发缓存写入（getById 会自动写入缓存）
                restaurantService.getById(vo.getId());
            }

            long endTime = System.currentTimeMillis();
            stringRedisTemplate.opsForValue().set(INIT_KEY,"true",TTL);
            log.info("========== 缓存预热完成，耗时: {}ms ==========", endTime - startTime);
        } catch (Exception e) {
            log.error("缓存预热失败", e);
            // 不抛异常，避免影响应用启动
        }
    }
}
