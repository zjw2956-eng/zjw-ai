package cn.zjw.mq.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订单创建事件
 */
@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    /**
     * 订单号
     */
    private final String orderNo;

    public OrderCreatedEvent(Object source, String orderNo) {
        super(source);
        this.orderNo = orderNo;
    }
}