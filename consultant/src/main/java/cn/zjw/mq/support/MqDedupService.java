package cn.zjw.mq.support;

import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import cn.zjw.common.constant.Constants;

@Component
public class MqDedupService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 接收消息体，返回MessageId
     * @param message
     * @return
     */
    public String extractMessageId(Message message) {
        if (message == null || message.getMessageProperties() == null) {
            return null;
        }
        return message.getMessageProperties().getMessageId();
    }

    /**
     * 返回true=首次消费；false=重复消费
     */
    public boolean tryMarkConsumed(String msgId) {
        if (msgId == null || msgId.isBlank()) {
            // 没有messageId时，不拦截（由业务幂等兜底）
            return true;
        }
        String key = Constants.MQ_CONSUMED_KEY_PREFIX + msgId;
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                Constants.MQ_CONSUMED_EXPIRE_SECONDS,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 业务异常时回滚去重标记，允许消息重试再次处理
     */
    public void rollback(String msgId){
        if(msgId==null || msgId.isBlank()){
            return;
        }
        stringRedisTemplate.delete(Constants.MQ_CONSUMED_KEY_PREFIX + msgId);
    }

}
