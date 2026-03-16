package cn.zjw.service;

import cn.zjw.pojo.dto.ReviewDTO;
import cn.zjw.pojo.entity.Review;
import com.baomidou.mybatisplus.extension.service.IService;
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
}
