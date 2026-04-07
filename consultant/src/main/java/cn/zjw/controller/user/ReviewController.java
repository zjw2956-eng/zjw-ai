package cn.zjw.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.pojo.dto.ReviewQueryDTO;
import cn.zjw.pojo.vo.MyReviewVO;
import cn.zjw.pojo.vo.ReviewVO;
import cn.zjw.service.ReviewService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;



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
        return CommonResult.success(ResultCode.SUCCESS, "评价发表成功",null);
    }
    

    /**
     * 审核评价，通过
     * TODO: 需要管理员/商家权限才能调用，待实现权限校验后开放,可以做个定时任务自动通过审核
     * @param id 评价ID
     */
    @PutMapping("/{id}/approve")
    public CommonResult<?> approveReview(@PathVariable Long id){
        reviewService.approveReview(id);
        return CommonResult.success(ResultCode.SUCCESS, "评价审核通过",null);
    }

    /**
     * 审核评价，拒绝
     * TODO: 需要管理员/商家权限才能调用，待实现权限校验后开放,可以做个定时任务自动拒绝审核
     * @param id 评价ID
     */
    @PutMapping("/{id}/reject")
    public CommonResult<?> rejectReview(@PathVariable Long id){
        reviewService.rejectReview(id);
        return CommonResult.success(ResultCode.SUCCESS, "评价审核拒绝",null);
    }

    /**
     * 查询餐厅评价列表
     */
    @GetMapping("/restaurant")
    public CommonResult<Page<ReviewVO>> listByRestaurantId(@Valid ReviewQueryDTO dto){
        Page<ReviewVO> result=reviewService.listByRestaurantId(dto);
        return CommonResult.success(ResultCode.SUCCESS, "查询餐厅评价列表成功",result);
    }


    /**
     * 用户查询自己的评价列表
     */
    @GetMapping("/myReviews")
    public CommonResult<Page<MyReviewVO>> listMyReviews(
        @RequestParam(defaultValue = "1") Integer current,
        @RequestParam(defaultValue = "10") Integer pageSize){
            if(current < 1){
                return CommonResult.error(ResultCode.BAD_REQUEST, "当前页码必须为正整数");
            }
            if(pageSize<1 || pageSize>100){
                return CommonResult.error(ResultCode.BAD_REQUEST, "查询页数在1-100之间");
            }
            Page<MyReviewVO> result=reviewService.listMyReviews(current,pageSize);
            return CommonResult.success(ResultCode.SUCCESS, "查询自己的评价列表成功",result);
        }
    
    /**
     * 查询评价详情
     * @param id 评价ID
     */
    @GetMapping("/myReviews/{id}")
    public CommonResult<MyReviewVO> getReviewDetail(@PathVariable Long id) {
        MyReviewVO result=reviewService.getReviewDetail(id);
        return CommonResult.success(ResultCode.SUCCESS, "查询评价详情成功",result);
    }
    
    /**
     * 删除评价
     * @param id 评价ID
     */
    @DeleteMapping("/myReviews/{id}")
    public CommonResult<Void> deleteMyReview(@PathVariable Long id){
        reviewService.deleteReview(id);
        return CommonResult.success(ResultCode.SUCCESS, "删除评价成功",null);
    }

}