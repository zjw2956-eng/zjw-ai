package cn.zjw.mapper;

import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * 评价Mapper
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 根据餐厅ID查询平均评分
     * @param restaurantId 餐厅ID
     * @return 平均评分
     * 查询该餐厅的平均评分，不包括已删除和审核未通过的评价
     */
    @Select("SELECT AVG(rating) FROM review WHERE restaurant_id = #{restaurantId} AND is_deleted = 0 AND status = 1")
    BigDecimal getAvgRatingByRestaurantId(@Param("restaurantId") Long restaurantId);
}
