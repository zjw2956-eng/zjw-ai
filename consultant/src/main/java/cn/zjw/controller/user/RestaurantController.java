package cn.zjw.controller.user;

import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.pojo.dto.RestaurantDTO;
import cn.zjw.pojo.dto.RestaurantQueryDTO;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.RestaurantService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

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
     * 支持按菜系、价格区间、最低评分筛选
     */
    @GetMapping("/list")
    public CommonResult<?> list(@Valid RestaurantQueryDTO query) {
        Page<RestaurantVO> page;
        if (StringUtils.hasText(query.getKeyword())) {
            // 有关键词：走 ES
            log.info("走ES搜索......");
            page = restaurantService.searchRestaurants(
                    query.getCurrent(), query.getPageSize(),
                    query.getKeyword(),
                    query.getCategory(), query.getMinPrice(),
                    query.getMaxPrice(), query.getMinRating());
        } else {
            // 无关键词：走原 MySQL
            log.info("走MySQL搜索......");
            page = restaurantService.listRestaurants(
                    query.getCurrent(), query.getPageSize(),
                    query.getCategory(), query.getMinPrice(),
                    query.getMaxPrice(), query.getMinRating());
        }
        return CommonResult.success(page);
    }

    /**
     * 根据ID查询餐厅详情
     */
    @GetMapping("/{id}")
    public CommonResult<RestaurantVO> getRestaurantById(@PathVariable Long id) {
        log.info("查询餐厅详情: id={}", id);
        RestaurantVO restaurantVO = restaurantService.getRestaurantById(id);
        return CommonResult.success(restaurantVO);
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
