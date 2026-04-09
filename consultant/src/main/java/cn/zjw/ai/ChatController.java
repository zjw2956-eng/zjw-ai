package cn.zjw.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.zjw.ai.service.AiHelperService;
import cn.zjw.common.context.UserContext;
import cn.zjw.common.result.CommonResult;
import dev.langchain4j.service.TokenStream;
import reactor.core.publisher.Flux;

/**
 * AI 聊天控制器
 *
 * 职责单一：只负责接收 HTTP 请求并转发给 ConsultantService，
 * 所有 AI 逻辑（RAG 检索、记忆加载、模型调用）均在 LangChain4j 框架内完成。
 */
@RequestMapping("/ai")
@RestController
public class ChatController {

    @Autowired
    private AiHelperService aiHelperService;

    /**
     * AI 对话（自动使用 RAG + 记忆）
     * GET /ai/chat?message=你好
     * memoryId 使用当前登录用户的 userId
     */
    @GetMapping("/chat")
    public CommonResult<String> chat(@RequestParam String message) {
        // 从 UserContext 获取当前登录用户ID，作为 memoryId
        Long userId = UserContext.getCurrentUserId();
        String response = aiHelperService.chat(userId.intValue(), message);
        return CommonResult.success(response);
    }

    /**
     * AI对话（流式返回，SSE）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String message) {
        Long userId = UserContext.getCurrentUserId();
        TokenStream tokenStream = aiHelperService.chatStream(userId.intValue(), message);

        return Flux.create(sink -> tokenStream
                .onPartialResponse(sink::next) // ✅ 每个 token 发送
                .onCompleteResponse(r -> sink.complete()) // ✅ 流结束
                .onError(sink::error) // ✅ 错误处理
                .start() // ✅ 开始生成
        );
    }

}
