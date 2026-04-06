package cn.zjw.search.init;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.zjw.search.service.RestaurantIndexService;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动初始化器：
 * 1) 确保索引存在
 * 2) 首次全量导入
 */
@Component
@Slf4j
public class RestaurantIndexInitializer implements CommandLineRunner {

    private static final String INIT_KEY = "es:restaurant:index:initialized";

    private static final Duration TTL = Duration.ofDays(300); // 300天过期

    @Autowired
    private RestaurantIndexService restaurantIndexService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (stringRedisTemplate.hasKey(INIT_KEY)) {
            log.info("餐厅索引已初始化，跳过全量导入");
            return;
        }
        log.info("开始初始化餐厅 ES 索引...");
        // 1.先建索引和mapping
        restaurantIndexService.createIndexIfNeeded();
        // 2.再导入数据（索引存在后再导入）
        restaurantIndexService.importAllRestaurants();
        stringRedisTemplate.opsForValue().set(INIT_KEY, "true", TTL);
        log.info("餐厅 ES 索引初始化完成");
    }

}
