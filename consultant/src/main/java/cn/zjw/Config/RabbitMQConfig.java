package cn.zjw.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.mq.message.ReviewAuditMessage;
@Slf4j
@Configuration
public class RabbitMQConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    RabbitMQConfig(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 配置消息转换器，将Java对象转换为JSON格式
     * @return
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        
        //发送消息确认
        // 1. 必须开启 mandatory，否则 ReturnsCallback 不会触发
        rabbitTemplate.setMandatory(true);
        //2.第一个回调：ConfirmCallback(确认消息发送成功，交换机确认)
        // 交换机确认消息后，会调用该回调
        // 可以在回调中更新数据库，标记消息已处理
        // 消息没到达交换机（网络或 Broker 问题）
        rabbitTemplate.setConfirmCallback(
            (correlationData,//（消息标识）
            ack,//（是否成功）
            cause//（失败原因）
            )->{
                String msgId=correlationData!=null?correlationData.getId():null;
                if(msgId==null){
                    log.warn("ConfirmCallback收到无ID消息，跳过处理");
                    return;
                }
                //如果ack为true，说明消息发送成功
                //如果ack为false，说明消息发送失败
                if(ack){
                    log.info("消息发送到交换机成功，msgId: {}，等待路由队列确认", msgId);
                }else{
                    log.error("消息发送到交换机失败，msgId: {}，原因: {}", msgId, cause);
                    handleMessageRetry(msgId, Constants.RABBITMQ_CORRELATION_MSG_ID+msgId,rabbitTemplate);
                }
            }
        );
        //3.第二个回调：ReturnsCallback(路由失败回退)
        // 消息到了交换机，但路由不到队列（routing key 写错了等）
        rabbitTemplate.setReturnsCallback(
            (returned //（RabbitMQ自动封装的返回的消息类）
            )->{
                Message message = returned.getMessage();
                String msgId = message.getMessageProperties().getHeader("spring_returned_message_correlation");
                if (msgId == null) {
                    log.warn("ReturnsCallback收到无ID消息，跳过处理");
                    return;
                }
                String exchange=returned.getExchange();
                String routingKey=returned.getRoutingKey();
                int replyCode=returned.getReplyCode();
                String replyText=returned.getReplyText();
                
                log.error("消息路由到队列失败，msgId: {}，交换机：{}，路由键：{}，回复码：{}，回复文本：{}",
                msgId, exchange, routingKey, replyCode, replyText);
                handleMessageRetry(msgId, Constants.RABBITMQ_CORRELATION_MSG_ID+msgId,rabbitTemplate);
            }
        );
        return rabbitTemplate;
    }
    

    /**
     * 处理消息重试
     * @param msgId 消息ID
     * @param string 缓存键
     * @param string2 回调类型
     */
    private void handleMessageRetry(String msgId, String redisKey, RabbitTemplate rabbitTemplate) {
        //1.从redis中取出消息
        String msgStr=(String) redisTemplate.opsForValue().get(redisKey);
        if(msgStr==null){
            log.warn("handleMessageRetry收到无消息体的消息，跳过处理");
            return;
        }
        Map<String,Object> msgMap=JSONUtil.toBean(msgStr, Map.class);
        //从消息体中获取消息体
        String msgJson=(String) msgMap.get("message");
        //从消息体中获取重试次数
        Integer retryCount=(Integer) msgMap.get("retryCount");        
        if(msgJson==null || retryCount==null){
            log.warn("handleMessageRetry收到无消息体或重试次数的消息，跳过处理");
            return;
        }
        //判断重试次数
        if(retryCount>Constants.MAX_RETRY_COUNT){
            log.warn("handleMessageRetry重试次数超过最大重试次数，msgId: {}", msgId);
            //缓存重试失败的消息
            redisTemplate.opsForValue().set(Constants.MQ_FAILED_RETRY_KEY+msgId, msgJson,
                Constants.MQ_FAILED_RETRY_EXPIRE_TIME, TimeUnit.SECONDS
            );
            //删除Redis重试信息的缓存
            redisTemplate.delete(redisKey);
            log.info("handleMessageRetry删除Redis缓存成功，msgId: {}", msgId);
        }else{
            //重试次数加1,并更新Redis缓存,原子操作
            msgMap.put("retryCount", retryCount+1);
            //更新Redis缓存
            redisTemplate.opsForValue().set(redisKey,JSONUtil.toJsonStr(msgMap),
                    Constants.MQ_RETRY_INTERVAL_TIME, TimeUnit.SECONDS
            );
            //反序列化
            ReviewAuditMessage msg=JSONUtil.toBean(msgJson, ReviewAuditMessage.class);
            //2.将消息重新发送到队列
            //将消息重新发送到队列
            CorrelationData correlationData=new CorrelationData(msgId);
            rabbitTemplate.convertAndSend("review.exchange", "review.audit", msg,correlationData);
            log.info("handleMessageRetry将消息重新发送到队列成功，msgId: {}", msgId);
        }


        
        
    }

    //死信交换机
    @Bean
    public DirectExchange reviewDlxExchange(){
        return new DirectExchange("review.dlx.exchange");
    }

    //死信队列（存放处理失败的消息）
    @Bean
    public Queue reviewAuditDlxQueue(){
        return QueueBuilder.durable("review.audit.dlx.queue").build();
    }

    //绑定死信队列到死信交换机
    @Bean
    public Binding reviewAuditDlxBinding(){
        return BindingBuilder
                .bind(reviewAuditDlxQueue())
                .to(reviewDlxExchange())
                .with("review.audit.dlx");
    }

    //定义交换机
    @Bean
    public DirectExchange reviewExchange(){
        return new DirectExchange("review.exchange");
    }

    //定义队列
    @Bean
    public Queue reviewAuditQueue(){
        return QueueBuilder.durable("review.audit.queue")
            .withArgument("x-dead-letter-exchange", "review.dlx.exchange")
            .withArgument("x-dead-letter-routing-key", "review.audit.dlx")
            .build();
    }

    //綁定隊列到交換機
    @Bean
    public Binding reviewAuditBinding(){
        return BindingBuilder
                .bind(reviewAuditQueue())
                .to(reviewExchange())
                .with("review.audit"); //路由鍵
    }

    // 评价通过队列
    @Bean
    public Queue reviewApprovedQueue() {
        return QueueBuilder.durable("review.approved.queue").build();
    }
    /**
     * 绑定评价通过队列到交换机
     */
    @Bean
    public Binding reviewApprovedBinding(){
        return BindingBuilder
                .bind(reviewApprovedQueue())
                .to(reviewExchange())
                .with("review.approved");
    }
}
