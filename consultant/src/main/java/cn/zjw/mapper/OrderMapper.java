package cn.zjw.mapper;

import cn.zjw.pojo.entity.OrderInfo;
import io.lettuce.core.dynamic.annotation.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderInfo> {

    /**
     * 查询用户订单历史，统计偏好菜系
     * @param userId 用户ID
     * @return List<Map<String, Object>> 菜系-订单数量
     */
    List<Map<String, Object>> selectUserPreferredCategories(@Param("userId") Long userId);
}
