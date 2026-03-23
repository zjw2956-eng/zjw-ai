package cn.zjw.ai;

import cn.zjw.tools.FoodReservationTool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import cn.zjw.repository.RedisChatMemoryStore;
@Configuration
public class AiServiceFactory {

    @Autowired
    private FoodReservationTool foodReservationTool;
    @Autowired
    private ChatModel qwenChatModel;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private RedisChatMemoryStore redisChatMemoryStore;
    @Bean
    public AiHelperService aiHelperService() {
        AiHelperService aiHelperService = AiServices.builder(AiHelperService.class)
                .chatModel(qwenChatModel)
                .contentRetriever(contentRetriever)//RAG检索增强生产
                .tools(foodReservationTool) //工具调用
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)//动态会话ID
                        .maxMessages(10) //保留最近10条消息
                        .chatMemoryStore(redisChatMemoryStore)//使用Redis持久化
                        .build())
                .build();
        return aiHelperService;
    }
}
