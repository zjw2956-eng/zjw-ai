package cn.zjw.controller;

import cn.zjw.service.OrderService;
import cn.zjw.pojo.dto.OrderDTO;
import cn.zjw.pojo.vo.OrderVO;
import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import cn.zjw.pojo.dto.OrderQueryDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 订单Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 创建订单
     * TODO: [幂等性] 未来添加幂等Token机制防止重复提交
     * 1. 新增 GET /api/order/token 接口，前端打开预订页时申请唯一Token
     * 2. OrderDTO 增加 idempotentToken 字段，前端提交时携带
     * 3. 此处校验 Token 是否存在（Redis NX），存在则放行并删除，不存在则拒绝
     */
    @PostMapping
    public CommonResult<Void> createOrder(@RequestBody @Valid OrderDTO dto) {
        orderService.createOrder(dto);
        return CommonResult.success(ResultCode.SUCCESS, "创建订单成功", null);
    }

    /**
     * 分页查询订单
     * 
     * @return
     */
    @GetMapping("/list")
    public CommonResult<?> listOrders(@Valid OrderQueryDTO dto) {
        Page<OrderVO> result = orderService.listOrders(dto.getCurrent(), dto.getPageSize(), dto.getStatus());
        return CommonResult.success(ResultCode.SUCCESS, "查询订单列表成功", result);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public CommonResult<OrderVO> getOrderDetail(@PathVariable String orderNo) {
        OrderVO orderVO = orderService.getOrderDetail(orderNo);
        if (orderVO == null) {
            return CommonResult.error(ResultCode.NOT_FOUND, "订单不存在");
        }
        return CommonResult.success(ResultCode.SUCCESS, "查询订单详情成功", orderVO);
    }

    /**
     * 取消订单
     */
    @PutMapping("/{orderNo}/cancel")
    public CommonResult<Void> cancelOrder(@PathVariable String orderNo) {
        // 实现取消订单逻辑（更新状态为 CANCELLED）
        orderService.cancelOrder(orderNo);
        return CommonResult.success(ResultCode.SUCCESS, "取消订单成功", null);
    }

    /**
     * 确认订单
     * 
     * @param orderNo
     * @return
     */
    @PutMapping("/{orderNo}/confirm")
    public CommonResult<Void> confirmOrder(@PathVariable String orderNo) {
        orderService.confirmOrder(orderNo);
        return CommonResult.success(ResultCode.SUCCESS, "确认订单成功", null);
    }

    /**
     * 完成订单
     * 
     * @param orderNo
     * @return
     */
    @PutMapping("/{orderNo}/complete")
    public CommonResult<Void> completeOrder(@PathVariable String orderNo) {
        orderService.completeOrder(orderNo);
        return CommonResult.success(ResultCode.SUCCESS, "确认订单完成成功", null);
    }
}
