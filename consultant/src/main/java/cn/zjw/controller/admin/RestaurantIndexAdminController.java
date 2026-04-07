package cn.zjw.controller.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.search.service.RestaurantIndexService;
import lombok.extern.slf4j.Slf4j;

/**
 * 餐厅索引管理接口
 * TODO: 后续应加管理员权限校验
 */
@RestController
@RequestMapping("/api/admin/es/restaurant")
@Slf4j
public class RestaurantIndexAdminController {

    @Autowired
    private RestaurantIndexService restaurantIndexService;

    /**
     * 重建索引：删除 -> 创建 -> 全量导入
     */
    @PostMapping("/rebuild")
    public CommonResult<Void> rebuild() {
        log.info("收到重建 restaurant 索引请求");
        restaurantIndexService.rebuildIndex();
        return CommonResult.success(ResultCode.SUCCESS, "重建索引成功", null);
    }

    /**
     * 仅全量导入（不删索引）
     */
    @PostMapping("/import")
    public CommonResult<Void> importAll() {
        log.info("收到全量导入 restaurant 索引请求");
        restaurantIndexService.importAllRestaurants();
        return CommonResult.success(ResultCode.SUCCESS, "全量导入成功", null);
    }

    /**
     * 删除索引（谨慎）
     */
    @DeleteMapping
    public CommonResult<Void> deleteIndex() {
        log.warn("收到删除 restaurant 索引请求");
        restaurantIndexService.deleteIndexIfExists();
        return CommonResult.success(ResultCode.SUCCESS, "删除索引成功", null);
    }
}