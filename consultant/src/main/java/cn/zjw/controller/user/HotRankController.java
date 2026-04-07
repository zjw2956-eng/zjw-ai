package cn.zjw.controller.user;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.zjw.common.result.CommonResult;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.HotRankService;
import lombok.extern.slf4j.Slf4j;

/**
 * 热门榜单排行控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/hot-rank")
public class HotRankController {

    @Autowired
    private  HotRankService hotRankService;

    @GetMapping("/restaurants")
    public CommonResult<List<RestaurantVO>> getHotRestaurants(
            @RequestParam(defaultValue = "10") Integer limit) {
        return CommonResult.success(hotRankService.getHotRestaurants(limit));
    }

    @PostMapping("/refresh")
    public CommonResult<Boolean> refreshHotRank() {
        hotRankService.refreshHotRestaurantRank();
        return CommonResult.success(true);
    }
}
