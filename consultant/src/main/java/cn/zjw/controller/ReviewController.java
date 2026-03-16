package cn.zjw.controller;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.service.ReviewService;

/**
 * 评价Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/review")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    /**
     * 发表评价
     */
    @PostMapping
    public CommonResult<?> createReview(@RequestBody @Valid ReviewDTO dto){
        log.info("发表评价:{}", dto);
        reviewService.createReview(dto);
        return CommonResult.success(ResultCode.SUCCESS, "评价发表成功");
    }
    

    /**
     * 审核评价，通过
     * TODO: 需要管理员/商家权限才能调用，待实现权限校验后开放,可以做个定时任务自动通过审核
     * @param id 评价ID
     */
    @PutMapping("/{id}/approve")
    public CommonResult<?> approveReview(@PathVariable Long id){
        reviewService.approveReview(id);
        return CommonResult.success(ResultCode.SUCCESS, "评价审核通过");
    }

    /**
     * 审核评价，拒绝
     * TODO: 需要管理员/商家权限才能调用，待实现权限校验后开放,可以做个定时任务自动拒绝审核
     * @param id 评价ID
     */
    @PutMapping("/{id}/reject")
    public CommonResult<?> rejectReview(@PathVariable Long id){
        reviewService.rejectReview(id);
        return CommonResult.success(ResultCode.SUCCESS, "评价审核拒绝");
    }
}