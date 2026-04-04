package cn.zjw.mq.event;

import org.springframework.context.ApplicationEvent;
import lombok.Getter;

/**
 * 评价创建事件
 */
@Getter
public class ReviewCreatedEvent extends ApplicationEvent {

    /**
     * 评价ID
     */
    private final Long reviewId;

    public ReviewCreatedEvent(Object source, Long reviewId) {
        super(source);
        this.reviewId = reviewId;
    }
}