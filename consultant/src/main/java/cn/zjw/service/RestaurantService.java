package cn.zjw.service;

import cn.zjw.pojo.vo.RestaurantVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 餐厅Service
 */
public interface RestaurantService {
    /**
     * 分页查询餐厅（返回VO）
     */
    Page<RestaurantVO> page(Integer pageNum, Integer pageSize, String category);

    /**
     * 根据ID查询餐厅详情
     */
    RestaurantVO getRestaurantById(Long id);
}
