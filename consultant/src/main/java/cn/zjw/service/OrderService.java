package cn.zjw.service;

import cn.zjw.pojo.dto.OrderDTO;
import cn.zjw.pojo.vo.OrderVO;
import cn.zjw.pojo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
/**
 * 订单Service
 */
public interface OrderService extends IService<OrderInfo> {

    /**
     * 创建订单
     */
    void createOrder(OrderDTO dto); 

    /**
     * 查询订单
     */
    Page<OrderVO> listOrders(Integer current, Integer pageSize,Integer status);

    /**
     * 查询订单详情
     */
    OrderVO getOrderDetail(String orderNo);

    
}
