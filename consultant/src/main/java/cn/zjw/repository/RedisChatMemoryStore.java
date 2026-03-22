package cn.zjw.repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.logging.LoggersEndpoint;
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
    @Autowired
    private LoggersEndpoint loggersEndpoint;


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
        String json = ChatMessageSerializer.messagesToJson(messages);
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + memoryId, json, TTL);
    }
}
