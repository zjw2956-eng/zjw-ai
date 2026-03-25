package cn.zjw.service;

import cn.zjw.pojo.dto.RestaurantDTO;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.vo.RestaurantVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import java.math.BigDecimal;
import java.util.List;

/**
 * 餐厅Service
 */
public interface RestaurantService extends IService<Restaurant> {
    /**
     * 分页查询餐厅，按评分降序
     */
    Page<RestaurantVO> listRestaurants(Integer current, Integer size,
            String category,BigDecimal minPrice,BigDecimal maxPrice,BigDecimal minRating);

    /**
     * 根据ID查询餐厅详情（带Redis缓存）
     */
    RestaurantVO getRestaurantById(Long id);

    /**
     * 新增餐厅
     */
    void addRestaurant(RestaurantDTO dto);

    /**
     * 修改餐厅
     */
    void updateRestaurant(RestaurantDTO dto);

    /**
     * 查询高分餐厅（用于推荐）
     */
    List<Restaurant> getTopRatedRestaurants(String category, BigDecimal minRating, Integer limit);
}
