package cn.zjw.config;



import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RedissonConfig {
    
    @Bean
    public RedissonClient redissonClient(){
        //配置类
        Config config=new Config();
        //添加redis地址
        config.useSingleServer().setAddress("redis://localhost:6379");
        //创建客户端
        return Redisson.create(config);
    }
}
