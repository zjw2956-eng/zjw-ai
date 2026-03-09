package cn.zjw.service.impl;

import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.service.RestaurantService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 餐厅Service实现
 */
@Service
@Slf4j
public class RestaurantServiceImpl implements RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Override
    public Page<Restaurant> page(Integer pageNum, Integer pageSize, String category) {
        // TODO: 实现分页查询逻辑
        return null;
    }

    @Override
    public Restaurant getById(Long id) {
        // TODO: 实现查询逻辑（含缓存）
        return null;
    }
}
