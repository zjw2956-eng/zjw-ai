package cn.zjw.controller;

import cn.zjw.common.result.PageResult;
import cn.zjw.common.result.Result;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.RestaurantService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
     * 分页查询餐厅
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页条数，默认10
     * @param category 菜系筛选（可选）：川菜、粤菜、湘菜、日料、西餐等
     */
    @GetMapping("/page")
    public Result<PageResult<RestaurantVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category) {
        log.info("分页查询餐厅: pageNum={}, pageSize={}, category={}", pageNum, pageSize, category);
        Page<RestaurantVO> result = restaurantService.page(pageNum, pageSize, category);
        return Result.success(PageResult.of(result));
    }

    /**
     * 根据ID查询餐厅详情
     *
     * @param id 餐厅ID
     */
    @GetMapping("/{id}")
    public Result<RestaurantVO> getRestaurantById(@PathVariable Long id) {
        log.info("查询餐厅详情: id={}", id);
        RestaurantVO restaurantVO = restaurantService.getRestaurantById(id);
        return Result.success(restaurantVO);
    }

    /**
     * 新增餐厅
     */
    @PostMapping("/add")
    public Result<RestaurantVO> addRestaurant(@RequestBody  RestaurantDTO dto){
        log.info("新增餐厅", dto);
        try {
            if(dto==null){
                return Result.error("新增餐厅不能为空");
            }
            restaurantService.addRestaurant(dto);
            return Result.success();
        } catch (Exception e) {
            // 处理异常
            log.error("新增餐厅失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 修改餐厅
     */
    @PutMapping("/update")
    public Result<RestaurantVO> updateRestaurant(@RequestBode RestaurantDTO dto){
        try {
            if(dto==null || dto.isBlank()){
                return Result.error("修改餐厅不能为空");
            }
            restaurantService.updateRestaurant(dto);
            return Result.success();
        } catch (Exception e) {
            // 处理异常
            log.error("修改餐厅失败", e);
            return Result.error(e.getMessage());
        }
    }
}
