package cn.zjw.mq.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.hutool.json.JSONUtil;
import cn.zjw.ai.model.ReviewAnalysisResult;
import cn.zjw.ai.service.ReviewAnalysisService;
import cn.zjw.common.enums.ReviewStatus;
import cn.zjw.mapper.ReviewMapper;
import cn.zjw.mq.message.ReviewAuditMessage;
import cn.zjw.pojo.entity.Review;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReviewAuditConsumer {

    @Autowired
    private ReviewAnalysisService reviewAnalysisService;
    

    @Autowired
    private ReviewMapper reviewMapper;
    
    @RabbitListener(queues = "review.audit.queue")
    public void handleReviewAudit(ReviewAuditMessage message){
        try {
            log.info("AI收到评价审核消息: {}", message);
            // ↓ 测试用，模拟AI审核失败，测完删掉
            if(true){throw new RuntimeException("模拟AI审核失败-测试死信队列");}
            
            //调用AI审核
            //返回结果ReviewAnalysisResult对象
            ReviewAnalysisResult result = reviewAnalysisService.analyzeReview(
                //传参评价内容和等级
                message.getContent(),
                message.getRating()
            );
            //更新数据库
            Review review=new Review();
            review.setId(message.getReviewId());
            review.setAiTags(JSONUtil.toJsonStr(result.tags()));
            review.setAiVerdict(result.verdict());

            switch(result.verdict()){
                case "APPROVE" -> review.setStatus(1); //已通过
                case "REJECT" -> review.setStatus(2); //已拒绝
                case "MANUAL_REVIEW" -> review.setStatus(0); //无法判断，要人工审核
            }
            reviewMapper.updateById(review);
            log.info("AI审核评价结果: {}", review);
        } catch (Exception e) {
            log.error("AI审核失败，reviewId={}: {}", message.getReviewId(),
                    e.getMessage(), e);
            // Spring AMQP 会自动重试3次，失败后自动路由到死信队列
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
