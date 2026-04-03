package cn.zjw.service.impl;

import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;

import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.mapper.ReviewMapper;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.service.RatingUpdateService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RatingUpdateServiceImpl implements RatingUpdateService {


    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private CacheClient cacheClient;
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

        // 2. 计算平均评分
        BigDecimal avgRating = reviewMapper.getAvgRatingByRestaurantId(restaurantId);
        if (avgRating == null) {
            log.warn("餐厅暂无评价，设置评分为0: restaurantId={}", restaurantId);
            avgRating = BigDecimal.ZERO;
        }

        // 3. 更新数据库
        restaurant.setRating(avgRating);
        restaurantMapper.updateById(restaurant);

        // 4. 删除缓存
        cacheClient.delete(Constants.REDIS_RESTAURANT_KEY + restaurantId);
        log.info("餐厅评分更新成功: restaurantId={}, newRating={}", restaurantId, avgRating);
    }

}
