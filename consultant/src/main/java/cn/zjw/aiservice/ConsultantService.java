package cn.zjw.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,//手动装配
        chatModel = "openAiChatModel",//指定模型
        streamingChatModel = "openAiStreamingChatModel",
        //chatMemory = "chatMemory",//配置会话记忆对象
        chatMemoryProvider = "chatMemoryProvider",//配置会话记忆提供者对象
        contentRetriever = "contentRetriever",//配置向量数据库检索对象
        tools = "foodReservationTool"//配置工具对象
)

public interface ConsultantService {

    //用于聊天的方法
//    public String chat(String message);
//    @SystemMessage("你是邹嘉炜的助手小盈，人美心善又多金")
    @SystemMessage(fromResource = "system.txt")
//    @UserMessage("你是邹嘉炜的女朋友小盈，人美心善又多金!{{it}}")
    public Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);


}
