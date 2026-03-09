package cn.zjw.controller;

import cn.zjw.common.result.PageResult;
import cn.zjw.common.result.Result;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.service.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 餐厅Controller
 */
@RestController
@RequestMapping("/api/restaurant")
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    /**
     * 分页查询餐厅
     */
    @GetMapping("/page")
    public Result<PageResult<Restaurant>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category) {
        // TODO: 实现分页查询
        return null;
    }

    /**
     * 根据ID查询餐厅详情
     */
    @GetMapping("/{id}")
    public Result<Restaurant> getById(@PathVariable Long id) {
        // TODO: 实现查询逻辑
        return null;
    }
}
