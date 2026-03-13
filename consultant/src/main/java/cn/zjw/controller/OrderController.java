package cn.zjw.controller;

import cn.zjw.common.result.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
public class OrderController {

    /**
     * 创建订单
     */
    @PostMapping
    public CommonResult<Void> createOrder() {
        // TODO: 实现创建订单逻辑
        return null;
    }

    /**
     * 查询我的订单
     */
    @GetMapping("/my")
    public CommonResult<Void> getMyOrders() {
        // TODO: 实现查询订单逻辑
        return null;
    }

    /**
     * 取消订单
     */
    @PutMapping("/{id}/cancel")
    public CommonResult<Void> cancelOrder(@PathVariable Long id) {
        // TODO: 实现取消订单逻辑
        return null;
    }
}
