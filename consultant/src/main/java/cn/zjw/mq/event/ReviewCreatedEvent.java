package cn.zjw.mq.event;

import cn.zjw.mq.message.ReviewAuditMessage;
import lombok.Getter;

@Getter
public class ReviewCreatedEvent {
    private final ReviewAuditMessage message;
    private final String msgId;

    public ReviewCreatedEvent(ReviewAuditMessage message, String msgId) {
        this.message = message;
        this.msgId = msgId;
    }
}