package cn.zjw.service;

public interface RatingUpdateService {
    /**
     * 更新餐厅评分
     * 
     * 该方法根据餐厅ID更新餐厅的评分。
     * 评分计算基于餐厅的所有评价，每个评价的权重为1分。
     * @param restaurantId 餐厅ID
     */
    void updateRestaurantRating(Long restaurantId);
}
