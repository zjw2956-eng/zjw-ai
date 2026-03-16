package cn.zjw.controller;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.service.OrderService;
import cn.zjw.pojo.dto.OrderDTO;
import cn.zjw.pojo.vo.OrderVO;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import cn.zjw.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import jakarta.validation.Valid;
import cn.zjw.pojo.dto.OrderQueryDTO;
import cn.hutool.core.util.PhoneUtil;
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

    @Autowired
    private RestaurantMapper restaurantMapper;

    /**
     * 创建订单
     * TODO: [幂等性] 未来添加幂等Token机制防止重复提交
     *   1. 新增 GET /api/order/token 接口，前端打开预订页时申请唯一Token
     *   2. OrderDTO 增加 idempotentToken 字段，前端提交时携带
     *   3. 此处校验 Token 是否存在（Redis NX），存在则放行并删除，不存在则拒绝
     */
    @PostMapping
    public CommonResult<Void> createOrder(@RequestBody OrderDTO dto) {
        Restaurant restaurant = restaurantMapper.selectById(dto.getRestaurantId());
        if (restaurant == null || restaurant.getIsDeleted() == 1 ) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"餐厅不存在");
        }
        // 检查餐厅是否正常
        if (restaurant.getStatus() != Constants.RESTAURANT_STATUS_NORMAL) {
            return CommonResult.error(ResultCode.NOT_FOUND,"餐厅休息中");
        }
        if(dto.getPeopleCount() <= 0) {
            return CommonResult.error(ResultCode.BAD_REQUEST,"用餐人数必须大于0");
        }
        if(dto.getReservationTime().isBefore(LocalDateTime.now())){
            return CommonResult.error(ResultCode.BAD_REQUEST,"预约时间不能早于当前时间");
        }
        if(!PhoneUtil.isMobile(dto.getContactPhone())){
            return CommonResult.error(ResultCode.BAD_REQUEST,"联系电话格式错误");
        }
        orderService.createOrder(dto);
        return CommonResult.success();
    }

    /**
     * 分页查询订单
     * @return
     */
    @GetMapping("/list")
    public CommonResult<?> listOrders(OrderQueryDTO dto){
        if(dto.getCurrent() < 1){
            return CommonResult.error(ResultCode.BAD_REQUEST,"当前页码必须为正整数");
        }
        if(dto.getPageSize() < 1 || dto.getPageSize() > 100){
            return CommonResult.error(ResultCode.BAD_REQUEST,"查询页数在1-100之间");
        }
        Page<OrderVO> result=orderService.listOrders(dto.getCurrent(), dto.getPageSize(),dto.getStatus());
        return CommonResult.success(result); 
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public CommonResult<OrderVO> getOrderDetail(@PathVariable String orderNo) {
        OrderVO orderVO=orderService.getOrderDetail(orderNo);
            if (orderVO==null) {
                return CommonResult.error(ResultCode.NOT_FOUND,"订单不存在");
            }
            return CommonResult.success(orderVO);
    }

    /**
     * 取消订单
     */
    @PutMapping("/{orderNo}/cancel")
    public CommonResult<Void> cancelOrder(@PathVariable String orderNo) {
        //实现取消订单逻辑（更新状态为 CANCELLED）
        orderService.cancelOrder(orderNo);
        return CommonResult.success();       
    }
}
