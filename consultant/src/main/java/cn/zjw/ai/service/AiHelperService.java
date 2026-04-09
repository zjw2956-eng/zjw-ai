package cn.zjw.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.TokenStream;

/**
 * AI 服务接口（核心入口）
 *
 * @AiService 是 LangChain4j 提供的注解，作用类似于 MyBatis 的 @Mapper：
 *            你只需声明接口，框架会在运行时自动生成代理实现类并注册为 Spring Bean。
 */
// @AiService
public interface AiHelperService {

   @SystemMessage(fromResource = "system.txt")
   String chat(@MemoryId int memoryId, @UserMessage String message);

   @SystemMessage(fromResource = "system.txt")
   TokenStream chatStream(@MemoryId int memoryId, @UserMessage String message);

}
