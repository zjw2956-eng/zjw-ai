package cn.zjw.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.zjw.ai.repository.RedisChatMemoryStore;
import cn.zjw.ai.service.AiHelperService;
import cn.zjw.ai.service.RestaurantSummaryService;
import cn.zjw.ai.service.ReviewAnalysisService;
import cn.zjw.ai.tools.FoodReservationTool;
import cn.zjw.ai.tools.RecommendationTool;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
@Configuration
public class AiServiceFactory {

    @Autowired
    private FoodReservationTool foodReservationTool;
    
    @Autowired
    private RecommendationTool recommendationTool;
    
    @Autowired
    private ChatModel qwenChatModel;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;

    @Autowired
    private McpToolProvider mcpToolProvider;


    @Bean
    public RestaurantSummaryService restaurantSummaryService() {
        return AiServices.builder(RestaurantSummaryService.class)
                .chatModel(qwenChatModel)
                .build();
    }

    @Bean
    public ReviewAnalysisService reviewAnalysisService() {
        return AiServices.builder(ReviewAnalysisService.class)
                .chatModel(qwenChatModel)
                .build();
    }

    @Bean
    public AiHelperService aiHelperService() {
        AiHelperService aiHelperService = AiServices.builder(AiHelperService.class)
                .chatModel(qwenChatModel)
                .contentRetriever(contentRetriever)//RAG检索增强生产
                .tools(foodReservationTool,recommendationTool) //工具调用
                .toolProvider(mcpToolProvider) //MCP工具调用
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)//动态会话ID
                        .maxMessages(10) //保留最近10条消息
                        .chatMemoryStore(redisChatMemoryStore)//使用Redis持久化
                        .build())
                .build();
        return aiHelperService;
    }
}
