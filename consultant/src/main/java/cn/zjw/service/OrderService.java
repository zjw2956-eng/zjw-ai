package cn.zjw.service;

import cn.zjw.pojo.dto.OrderDTO;
import cn.zjw.pojo.vo.OrderVO;
import cn.zjw.pojo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 订单Service
 */
public interface OrderService extends IService<OrderInfo> {

    /**
     * 创建订单
     * 
     * @param dto 订单DTO
     * 
     */
    void createOrder(OrderDTO dto);

    /**
     * 查询订单
     */
    Page<OrderVO> listOrders(Integer current, Integer pageSize, Integer status);

    /**
     * 查询订单详情
     */
    OrderVO getOrderDetail(String orderNo);

    /**
     * 取消订单
     * 
     * @param orderNo 订单号
     * 
     */
    void cancelOrder(String orderNo);

    /**
     * 餐厅确认订单
     */
    void confirmOrder(String orderNo);

    /**
     * 餐厅确认完成订单
     * 
     * @param userId
     * @return
     */
    void completeOrder(String orderNo);

    /**
     * 查询用户订单历史，统计偏好菜系
     * 
     * @return Map<菜系, 订单数量>
     */
    Map<String, Integer> getUserPreferredCategories(Long userId);

}
