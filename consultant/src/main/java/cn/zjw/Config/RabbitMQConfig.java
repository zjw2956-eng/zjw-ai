package cn.zjw.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

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
        return rabbitTemplate;
    }
    
    //定义交换机
    @Bean
    public DirectExchange reviewExchange(){
        return new DirectExchange("review.exchange");
    }

    //定义队列
    @Bean
    public Queue reviewAuditQueue(){
        return QueueBuilder.durable("review.audit.queue").build();
    }

    //綁定隊列到交換機
    @Bean
    public Binding reviewAuditBinding(){
        return BindingBuilder
                .bind(reviewAuditQueue())
                .to(reviewExchange())
                .with("review.audit"); //路由鍵
    }
}
