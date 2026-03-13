package cn.zjw.controller;

import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.pojo.dto.RestaurantDTO;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.RestaurantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 餐厅Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/restaurant")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    /**
     * 分页查询餐厅，按评分降序
     *
     * @param current 当前页码，默认1
     * @param size    每页条数，默认10
     */
    @GetMapping("/list")
    public CommonResult<?> list(@RequestBody RestaurantQueryDTO query) {
        try {
            if (query.getCurrent() < 1) {
                return CommonResult.error(ResultCode.BAD_REQUEST, "当前页必须为正整数");
            }
            if (query.getSize() < 1 || query.getSize() > 100) {
                return CommonResult.error(ResultCode.BAD_REQUEST, "每页条数须在1~100之间");
            }
            return restaurantService.listRestaurants(query.getCurrent(), query.getSize(),
                    query.getCategory(),query.getMinPrice(),query.getMaxPrice(),query.getMinRating());
        } catch (Exception e) {
            log.error("查询餐厅失败", e);
            return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR, "查询餐厅失败");
        }
    }

    /**
     * 根据ID查询餐厅详情
     *
     * @param id 餐厅ID
     */
    @GetMapping("/{id}")
    public CommonResult<RestaurantVO> getRestaurantById(@PathVariable Long id) {
        try {
            log.info("查询餐厅详情: id={}", id);
            RestaurantVO restaurantVO = restaurantService.getRestaurantById(id);
            return CommonResult.success(restaurantVO);
        } catch (Exception e) {
            log.error("查询餐厅详情失败", e);
            return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR, "查询餐厅详情失败");
        }
    }

    /**
     * 新增餐厅
     * TODO: 需要管理员/商家权限才能调用，待实现权限校验后开放
     */
    @PostMapping("/add")
    public CommonResult<Void> addRestaurant(@RequestBody RestaurantDTO dto) {
        // TODO: 调用 restaurantService.addRestaurant(dto)
        return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR, "功能待实现");
    }

    /**
     * 修改餐厅
     * TODO: 需要管理员/商家权限才能调用，待实现权限校验后开放
     */
    @PutMapping("/update")
    public CommonResult<Void> updateRestaurant(@RequestBody RestaurantDTO dto) {
        // TODO: 参数校验 + 调用 restaurantService.updateRestaurant(dto)
        return CommonResult.error(ResultCode.INTERNAL_SERVER_ERROR, "功能待实现");
    }
}
