package cn.zjw.mq.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.hutool.json.JSONUtil;
import cn.zjw.ai.model.ReviewAnalysisResult;
import cn.zjw.ai.service.ReviewAnalysisService;

import cn.zjw.common.enums.ReviewStatus;
import cn.zjw.mapper.ReviewMapper;
import cn.zjw.mq.message.ReviewAuditMessage;
import cn.zjw.pojo.entity.Review;
import cn.zjw.service.ReviewService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReviewAuditConsumer {

    @Autowired
    private ReviewAnalysisService reviewAnalysisService;
    

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private ReviewService reviewService;
    
    @RabbitListener(queues = "review.audit.queue")
    public void handleReviewAudit(ReviewAuditMessage message){
        try {
            log.info("AI收到评价审核消息: {}", message);
            //调用AI审核
            //返回结果ReviewAnalysisResult对象
            ReviewAnalysisResult result = reviewAnalysisService.analyzeReview(
                //传参评价内容和等级
                message.getContent(),
                message.getRating()
            );

            String verdict = result.verdict() == null ? "MANUAL_REVIEW"
              : result.verdict().trim().toUpperCase();
            //统一设置AI字段
            Review review=new Review();
            review.setId(message.getReviewId());
            review.setAiTags(JSONUtil.toJsonStr(result.tags()));
            review.setAiVerdict(verdict);
            reviewMapper.updateById(review); 
            //再按 verdict 走状态流转
            switch (verdict) {
                case "APPROVE" -> reviewService.approveReview(message.getReviewId());
                case "REJECT" -> reviewService.rejectReview(message.getReviewId());
                case "MANUAL_REVIEW" -> {
                    Review manual = new Review();
                    manual.setId(message.getReviewId());
                    manual.setStatus(ReviewStatus.PENDING.getCode()); // 人工审核
                    reviewMapper.updateById(manual);
                }
                default -> {
                    Review unknown=new Review();
                    unknown.setId(message.getReviewId());
                    unknown.setAiVerdict("MANUAL_REVIEW");
                    unknown.setStatus(ReviewStatus.PENDING.getCode()); // 人工审核
                    reviewMapper.updateById(unknown);
                    log.error("AI审核失败，reviewId={}: {}", message.getReviewId(),
                            "未知审核结果");
                }
            }
            log.info("消费者确认.....");
        } catch (Exception e) {
            // 真异常才标 AI_ERROR
            Review fail = new Review();
            fail.setId(message.getReviewId());
            fail.setAiVerdict("AI_ERROR");
            fail.setStatus(ReviewStatus.PENDING.getCode());
            reviewMapper.updateById(fail);
            throw new RuntimeException("AI审核失败", e);
        }
    }

    @RabbitListener(queues = "review.audit.dlx.queue")
    public void handleFailedReview(ReviewAuditMessage message){
        log.error("AI审核失败，转人工审核,reviewId={}", message.getReviewId());
        Review review=new Review();
        review.setId(message.getReviewId());
        review.setAiVerdict("AI_ERROR");
        review.setStatus(ReviewStatus.PENDING.getCode()); //人工审核
        reviewMapper.updateById(review);
    }
}
