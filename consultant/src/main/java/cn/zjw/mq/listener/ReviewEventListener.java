package cn.zjw.mq.listener;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cn.zjw.mq.event.ReviewCreatedEvent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReviewEventListener{
    
    @Autowired
    private RabbitTemplate rabbitTemplate;


    /**
     * 监听评价创建事件，在事务提交后发送 MQ 消息
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewCreated(ReviewCreatedEvent event){
        log.info("事务已提交，发送MQ消息，msgId: {}", event.getMsgId());
        //生产者确认CorrelationData是否成功
        CorrelationData correlationData=new CorrelationData(event.getMsgId());
        rabbitTemplate.convertAndSend("review.exchange", "review.audit", event.getMessage(),correlationData);
        log.info("MQ消息发送成功，msgId: {}", event.getMsgId());
    }
    
}
