package cn.zjw.ai;

import cn.zjw.common.result.CommonResult;
import dev.langchain4j.service.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
     * GET /ai/chat?memoryId=123&message=你好
     */
    @GetMapping("/chat")
    public CommonResult<String> chat(@RequestParam Integer memoryId,
                                    @RequestParam String message) {
        String response = aiHelperService.chat(memoryId, message);
        return CommonResult.success(response);
    }




}
