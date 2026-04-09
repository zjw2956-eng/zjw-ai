package cn.zjw.mq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.zjw.search.service.RestaurantIndexService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RestaurantEsSyncConsumer {

    @Autowired
    private RestaurantIndexService restaurantIndexService;
    
    @RabbitListener(queues = "restaurant.es.sync.queue")
    public void handleEsSync(Long restaurantId, Message message) {
        try {
            restaurantIndexService.syncRestaurantById(restaurantId);
        } catch (Exception e) {
            // 不影响主流程：评分已写库成功，ES 同步失败记录日志即可
            log.error("同步餐厅评分到ES失败: restaurantId={}", restaurantId, e);
        }
    }

}
