package cn.zjw.service;

import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.zjw.pojo.dto.ReviewQueryDTO;
import cn.zjw.pojo.vo.MyReviewVO;
import cn.zjw.pojo.vo.ReviewVO;

public interface ReviewService extends IService<Review> {
    /**
     * 发表评价
     */
    void createReview(ReviewDTO dto);

    /**
     * 审核评价，通过
     */
    void approveReview(Long id);    

    /**
     * 审核评价，拒绝
     */
    void rejectReview(Long id);

    /**
     * 查询餐厅评价列表
     */
    Page<ReviewVO> listByRestaurantId(ReviewQueryDTO dto);

    /**
     * 用户查询自己的评价列表
     */
    Page<MyReviewVO> listMyReviews(Integer current, Integer pageSize);

    /**
     * 查询评价详情
     */
    MyReviewVO getReviewDetail(Long id);

    /**
     * 删除评价
     */
    void deleteReview(Long id);

    /**
     * 查询用户评价历史（用于分析偏好）
     */
    List<MyReviewVO> getUserReviewHistory(@Param("userId") Long userId, @Param("limit") Integer limit);
}
