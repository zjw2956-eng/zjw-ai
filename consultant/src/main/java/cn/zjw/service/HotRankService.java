package cn.zjw.service;

import java.util.List;

import cn.zjw.pojo.vo.RestaurantVO;

public interface HotRankService {

    /**
     * 刷新餐厅热门榜单到 Redis（近30天已支付订单数）
     */
    void refreshHotRestaurantRank();

    /**
     * 获取热门餐厅ID（按热度倒序）
     */
    List<Long> getHotRestaurantIds(int limit);

    /**
     * 获取热门餐厅详情（按榜单顺序返回）
     */
    List<RestaurantVO> getHotRestaurants(int limit);

}
