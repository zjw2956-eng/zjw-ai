package cn.zjw.mapper;

import cn.zjw.pojo.entity.OrderInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderInfo> {
}
