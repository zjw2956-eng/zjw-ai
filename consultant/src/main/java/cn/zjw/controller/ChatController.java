package cn.zjw.controller;

import cn.zjw.aiservice.ConsultantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 聊天控制器
 *
 * 职责单一：只负责接收 HTTP 请求并转发给 ConsultantService，
 * 所有 AI 逻辑（RAG 检索、记忆加载、模型调用）均在 LangChain4j 框架内完成。
 */
@RestController
public class ChatController {


    @Autowired
    private ConsultantService consultantService;

    
    /**
     * SSE 流式对话接口
     *
     * GET /chat?memoryId=xxx&message=xxx
     *
     * produces = TEXT_EVENT_STREAM_VALUE：
     *   声明响应类型为 Server-Sent Events（SSE），
     *   浏览器/前端可以用 EventSource 或 fetch + ReadableStream 持续接收数据流，
     *   每收到一个 token 就立即渲染，形成打字机效果。
     *
     * memoryId：
     *   前端每次新建会话时生成一个 UUID 传过来（见 index.html），
     *   同一个对话窗口始终使用同一个 memoryId，保证上下文连贯。
     *
     * message：
     *   用户本次输入的内容，URL 参数传递。
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String memoryId,
                             @RequestParam String message){
        return consultantService.chat(memoryId, message);
    }
    
}
