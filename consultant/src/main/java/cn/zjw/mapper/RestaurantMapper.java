package cn.zjw.mapper;

import cn.zjw.pojo.entity.Restaurant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

/**
 * 餐厅Mapper
 */
@Mapper
public interface RestaurantMapper extends BaseMapper<Restaurant> {

    /**
     * 原子更新餐厅评分（直接用 SQL 计算，避免并发问题）
     *
     * @param restaurantId 餐厅ID
     * @return 影响行数
     */
    @Update("UPDATE restaurant SET " +
            "rating = (SELECT IFNULL(AVG(rating), 0) FROM review WHERE restaurant_id = #{restaurantId} AND status = 'APPROVED'), " +
            "update_time = NOW() " +
            "WHERE id = #{restaurantId}")
    int updateRatingBySQL(@Param("restaurantId") Long restaurantId);
}
