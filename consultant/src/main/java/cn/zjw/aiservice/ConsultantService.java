package cn.zjw.aiservice;

import dev.langchain4j.service.*;
import dev.langchain4j.service.spring.*;
import reactor.core.publisher.Flux;


/**
 * AI 服务接口（核心入口）
 *
 * @AiService 是 LangChain4j 提供的注解，作用类似于 MyBatis 的 @Mapper：
 * 你只需声明接口，框架会在运行时自动生成代理实现类并注册为 Spring Bean。
 *
 * wiringMode = EXPLICIT 表示"显式装配"：
 *   框架不会自动猜测该用哪些 Bean，而是严格按照下面声明的 Bean 名称去找。
 *   好处是配置清晰，不会因为容器里有多个同类型 Bean 而出现歧义。
 *
 * streamingChatModel = "openAiStreamingChatModel"：
 *   langchain4j-open-ai-spring-boot-starter 会根据 application.yaml 中
 *   langchain4j.open-ai.chat-model 的配置，自动创建名为 openAiStreamingChatModel
 *   的流式对话模型 Bean，这里直接引用其 Bean 名称即可，无需手动定义。
 *
 * chatMemoryProvider = "chatMemoryProvider"：
 *   引用 AiConfig 中手动定义的 ChatMemoryProvider Bean，
 *   该 Bean 内部使用 RedisChatMemoryStore 实现多会话隔离。
 *
 * contentRetriever = "contentRetriever"：
 *   引用 AiConfig 中定义的 RAG 检索器 Bean，
 *   每次对话前会自动检索 Redis 向量库中最相关的知识片段，拼入 Prompt。
 */
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    streamingChatModel = "openAiStreamingChatModel",
    chatMemoryProvider = "chatMemoryProvider",
    contentRetriever = "contentRetriever"
)
public interface ConsultantService {
    
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);
}
