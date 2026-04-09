package cn.zjw.service.impl;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.IdUtil;
import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.mapper.ReviewMapper;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.search.service.RestaurantIndexService;
import cn.zjw.service.RatingUpdateService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RatingUpdateServiceImpl implements RatingUpdateService {

    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private RestaurantIndexService restaurantIndexService;

    @Autowired
    private RabbitTemplate rabbitTemplate;
    /**
     * 更新餐厅评分
     * 计算餐厅的平均评分，并更新到餐厅表
     *
     *
     * @param restaurantId 餐厅ID
     */
    @Override
    public void updateRestaurantRating(Long restaurantId) {
        // 1. 查询餐厅是否存在
        Restaurant restaurant = restaurantMapper.selectById(restaurantId);
        if (restaurant == null) {
            log.warn("餐厅不存在，跳过评分更新: restaurantId={}", restaurantId);
            return; // 直接返回，不抛异常（让消费者 ACK，不重试）
        }

        // 2. 使用 SQL 原子更新评分（避免并发问题）
        int rows = restaurantMapper.updateRatingBySQL(restaurantId);
        if (rows == 0) {
            log.warn("餐厅评分更新失败，餐厅可能已被删除: restaurantId={}", restaurantId);
            return;
        }

        // 3. 删除缓存
        cacheClient.delete(Constants.REDIS_RESTAURANT_KEY + restaurantId);

        // 4. 发送MQ消息，异步同步到ES
        try {
            // 构造 msgId
            String msgId = IdUtil.simpleUUID();
            // 发送消息到评价消息队列
            rabbitTemplate.convertAndSend(
                    "restaurant.es.sync.exchange", // 评价交换机
                    "restaurant.es.sync",
                    restaurantId,
                    msg -> {
                        msg.getMessageProperties().setMessageId(msgId);
                        return msg;
                    },
                    new CorrelationData(msgId));
            log.info("异步更新ES的MQ消息发送成功，msgId: {}", msgId);
        } catch (Exception e) {
            // 不影响主流程：评分已写库成功，ES 同步失败记录日志即可
            log.error("同步餐厅评分到ES失败: restaurantId={}", restaurantId, e);
        }
        log.info("餐厅评分更新成功: restaurantId={}", restaurantId);
    }

}
