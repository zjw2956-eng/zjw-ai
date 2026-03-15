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
     */
    @PostMapping
    public CommonResult<Void> createOrder(@RequestBody OrderDTO dto) {
        try {
            Restaurant restaurant = restaurantMapper.selectById(dto.getRestaurantId());
            if (restaurant == null || restaurant.getIsDeleted() == 1 ) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"餐厅不存在");
            }
            // 检查餐厅是否正常
            if (restaurant.getStatus() != Constants.RESTAURANT_STATUS_NORMAL) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"餐厅休息中");
            }
           if(dto.getPeopleCount() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"用餐人数必须大于0");
            }
            if(dto.getReservationTime().isBefore(LocalDateTime.now())){
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"预约时间不能早于当前时间");
            }
            if(!PhoneUtil.isMobile(dto.getContactPhone())){
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"联系电话格式错误");
            }
            orderService.createOrder(dto);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR,"创建订单失败");
        }
        return CommonResult.success();
    }

    /**
     * 分页查询订单
     * @return
     */
    @GetMapping("/list")
    public CommonResult<?> listOrders(@RequestBody OrderQueryDTO dto){
        try {
            if(dto.getCurrent() < 1){
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"当前页码必须为正整数");
            }
            if(dto.getPageSize() < 1 || dto.getPageSize() > 100){
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"查询页数在1-100之间");
            }
            Page<OrderVO> result=orderService.listOrders(dto.getCurrent(), dto.getPageSize(),dto.getStatus());
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("查询订单失败", e);
            return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR,"查询订单失败");
        }
        
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
