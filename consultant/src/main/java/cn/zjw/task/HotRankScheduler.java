package cn.zjw.task;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cn.zjw.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HotRankScheduler {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Scheduled(cron = "0 0 2 * * ?")
    public void refresh() {
        // 多实例部署下，只让抢到锁的节点发消息，避免队列堆积重复消息
        RLock lock = redissonClient.getLock(Constants.REDIS_LOCK_RESTAURANT_HOT_RANK_KEY + ":schedule");
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.info("热门榜单定时任务：未抢到锁，跳过（其他实例已发送）");
                return;
            }
            log.info("热门榜单定时任务触发，发送刷新消息到MQ");
            rabbitTemplate.convertAndSend("hot.rank.refresh.exchange", "hot.rank.refresh", "refresh");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}


