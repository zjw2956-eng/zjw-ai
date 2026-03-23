package cn.zjw.repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
@Component
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore{

    private static final String KEY_PREFIX ="chat:memory:";
    private static final Duration TTL=Duration.ofDays(1);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 删除会话消息
     * @param memoryId 会话ID
     */
    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(KEY_PREFIX + memoryId);
    }

    /**
     * 获取会话消息
     * @param memoryId 会话ID
     * @return 消息列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.info("读取会话记忆："+memoryId);
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + memoryId);
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    /**
     * 更新会话消息
     * @param memoryId 会话ID
     * @param messages 消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        log.info("保存会话记忆："+memoryId+",消息数量："+messages.size());
        //修复消息顺序：确保第一条非系统消息是USerMessage
        List<ChatMessage> fixedMessages=fixMessageOrder(messages);
        String json = ChatMessageSerializer.messagesToJson(fixedMessages);
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + memoryId, json, TTL);
    }


    /**
     * 修复消息顺序，确保第一条非系统消息是 UserMessage
     */
    private List<ChatMessage> fixMessageOrder(List<ChatMessage> messages) {
        if (messages == null || messages.size() < 2) {
            return messages;
        }

        // 找到第一条非系统消息的索引
        int firstNonSystemIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (!(messages.get(i) instanceof dev.langchain4j.data.message.SystemMessage)) {
                firstNonSystemIndex = i;
                break;
            }
        }

        // 如果第一条非系统消息不是 UserMessage，需要调整
        if (firstNonSystemIndex != -1
                && !(messages.get(firstNonSystemIndex) instanceof dev.langchain4j.data.message.UserMessage)) {

            // 找到第一条 UserMessage
            for (int i = firstNonSystemIndex + 1; i < messages.size(); i++) {
                if (messages.get(i) instanceof dev.langchain4j.data.message.UserMessage) {
                    // 交换位置
                    List<ChatMessage> fixed = new java.util.ArrayList<>(messages);
                    ChatMessage temp = fixed.get(firstNonSystemIndex);
                    fixed.set(firstNonSystemIndex, fixed.get(i));
                    fixed.set(i, temp);
                    return fixed;
                }
            }
        }

        return messages;
    }

}
