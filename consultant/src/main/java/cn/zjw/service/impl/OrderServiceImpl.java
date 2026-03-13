package cn.zjw.service.impl;

import cn.zjw.service.OrderService;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;


/**
 * 订单Service实现
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderInfo> implements OrderService {
    
}
