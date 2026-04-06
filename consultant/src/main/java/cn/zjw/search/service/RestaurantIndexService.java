package cn.zjw.search.service;

/**
 * 餐厅索引服务
 */
public interface RestaurantIndexService {
    /**
     * 创建餐厅索引（不存在时）
     */
    void createIndexIfNeeded();

    /**
     * 全量导入 MySQL 中的餐厅数据到 ES
     */
    void importAllRestaurants();

    /**
     * 同步单个餐厅到 ES（新增/修改/评分变更后复用）
     */
    void syncRestaurantById(Long restaurantId);

    /**
     * 从 ES 删除餐厅文档
     */
    void deleteRestaurantById(Long restaurantId);

}
