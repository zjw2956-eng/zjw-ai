package cn.zjw.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j  
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private String port;

    @Bean
    public RedissonClient redissonClient() {
        try {
            Config config = new Config();
            config.useSingleServer()
                    .setAddress("redis://" + host + ":" + port)
                    .setDatabase(0)
                    .setConnectionPoolSize(64)
                    .setConnectionMinimumIdleSize(10)
                    .setTimeout(10000)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);
            RedissonClient client = Redisson.create(config);
            log.info("✅ Redisson连接成功: redis://" + host + ":" + port);
            return client;
        } catch (Exception e) {
            log.error("❌ Redisson连接失败: redis://" + host + ":" + port, e);
            throw new RuntimeException("Redisson初始化失败，请检查Redis是否启动", e);
        }
    }
}
