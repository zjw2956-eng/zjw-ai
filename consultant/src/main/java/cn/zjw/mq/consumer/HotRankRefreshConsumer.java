package cn.zjw.mq.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.zjw.service.HotRankService;
import lombok.extern.slf4j.Slf4j;

/**
 * 热门榜单异步刷新消费者
 * 定时任务只负责发消息，实际刷新逻辑在这里执行
 * refreshHotRestaurantRank 内部已有 Redisson 分布式锁，多实例部署下只有一个节点真正执行
 */
@Component
@Slf4j
public class HotRankRefreshConsumer {

    @Autowired
    private HotRankService hotRankService;

    @RabbitListener(queues = "hot.rank.refresh.queue")
    public void handleHotRankRefresh(String message) {
        log.info("收到热门榜单刷新消息，开始刷新");
        try {
            hotRankService.refreshHotRestaurantRank();
            log.info("热门榜单刷新完成");
        } catch (Exception e) {
            log.error("热门榜单刷新失败", e);
            throw new RuntimeException("热门榜单刷新失败", e);
        }
    }
}
