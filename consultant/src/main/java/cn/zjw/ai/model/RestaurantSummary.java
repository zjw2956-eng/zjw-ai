package cn.zjw.ai.model;

import java.util.List;

public record RestaurantSummary(
        String summary, // 总体摘要
        List<String> highlights, // 亮点
        List<String> warnings // 注意事项
) {}