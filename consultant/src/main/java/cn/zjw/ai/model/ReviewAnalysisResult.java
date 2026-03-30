package cn.zjw.ai.model;

import java.util.List;

public record ReviewAnalysisResult(
    String verdict,     // APPROVE/REJECT/MANUAL_REVIEW
    String reason,      // 审核结果原因
    List<String> tags   //情感标签：["服务热情"，"环境一般"]
){}
