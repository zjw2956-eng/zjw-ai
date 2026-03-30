package cn.zjw.ai.service;

import cn.zjw.ai.model.ReviewAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ReviewAnalysisService {


    @SystemMessage("""
                你是一个评价审核助手。
                审核规则（严格执行）：
                1. 检查文字与评分是否一致：
                   - 1-2星（差评）但内容全是好话（如"好吃"、"推荐"、"不错"）→ 返回MANUAL_REVIEW
                   - 4-5星（好评）但内容全是差评（如"难吃"、"垃圾"、"不推荐"）→返回 MANUAL_REVIEW

                2. 内容正常且文字与评分一致 → 返回 APPROVE

                3. 含隐晦广告（如"加微信"、"联系我"、"私聊"）、辱骂、色情暗示 →
            返回 REJECT

                同时提取情感标签（3-5个），如：["服务热情", "环境嘈杂","性价比高",
             "菜品新鲜"]
                注意：已经过滤了明显敏感词，你需要识别隐晦的不当内容和评分矛盾。
                """)
    ReviewAnalysisResult analyzeReview(
            @UserMessage String reviewContent,
            @V("rating") Integer rating);
}

    

