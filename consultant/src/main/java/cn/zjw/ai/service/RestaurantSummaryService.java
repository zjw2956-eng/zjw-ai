package cn.zjw.ai.service;

import cn.zjw.ai.model.RestaurantSummary;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface RestaurantSummaryService {
        
    @SystemMessage("""
                你是一个餐厅口碑分析助手。
                根据用户评价，生成餐厅的整体口碑摘要。

                要求（必须严格按照格式返回）：
                1. summary：用1-2句话总结餐厅整体评价（50字以内），不能为空
                2. highlights：提取3-5个亮点（如"招牌菜好吃"、"服务热情"），至少3个
                3. warnings：提取1-3个注意事项（如"环境嘈杂"、"需要提前预约"），如果没有明显问题就写
            ["暂无"]

                示例输出格式：
                {"summary": "用户普遍认为菜品好吃，服务热情，性价比高",
                  "highlights": ["菜品好吃", "服务热情", "性价比高"],
                  "warnings": ["环境稍显嘈杂"]
                }

                注意：客观中立，基于评价内容总结，不要编造，但必须返回完整的三个字段。
                """)
    RestaurantSummary generateSummary(@UserMessage String reviews);
}
