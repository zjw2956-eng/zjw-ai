package cn.zjw.service;

import cn.zjw.pojo.entity.Restaurant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 餐厅Service
 */
public interface RestaurantService {
    /**
     * 分页查询餐厅
     */
    Page<Restaurant> page(Integer pageNum, Integer pageSize, String category);

    /**
     * 根据ID查询餐厅
     */
    Restaurant getById(Long id);
}
