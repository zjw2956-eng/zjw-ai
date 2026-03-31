# RabbitMQ 消息确认与死信队列 - 项目实战总结

> 基于智能美食推荐系统中的 **AI 评论审核** 异步处理场景

---

## 一、项目背景与业务场景

### 为什么用 RabbitMQ？

**业务痛点**：用户提交餐厅评论后，需要调用 AI 大模型（通义千问）进行内容审核，但 AI 调用耗时较长（1~3秒），如果同步处理会导致用户等待时间过长。

**解决方案**：
- 用户提交评论 → 立即返回"审核中"
- 后台发送消息到 RabbitMQ
- 消费者异步调用 AI 审核
- 审核完成后更新数据库状态

**技术选型理由**：
- RabbitMQ 支持消息持久化、死信队列、延迟重试等可靠性机制
- Spring AMQP 集成简单，开箱即用

---

## 二、消息确认机制（Acknowledge）

### 2.1 三种确认模式对比

| 模式 | 触发时机 | 适用场景 | 风险 |
|------|---------|---------|------|
| **NONE** | 消息发出即确认 | 日志收集、非关键通知 | 消息可能丢失 |
| **AUTO**（默认） | 方法正常返回 → ACK<br>方法抛异常 → NACK | 大部分业务场景 | 需保证幂等性 |
| **MANUAL** | 手动调用 `channel.basicAck()` | 金融支付、库存扣减 | 代码复杂度高 |

### 2.2 项目中的实现（AUTO 模式）

**配置层面**：未显式配置，使用 Spring AMQP 默认的 AUTO 模式

**代码层面**：
```java
@RabbitListener(queues = "review.audit.queue")
public void handleReviewAudit(ReviewAuditMessage message){
    try {
        // 1. 调用 AI 审核
        ReviewAnalysisResult result = reviewAnalysisService.analyzeReview(...);
        
        // 2. 更新数据库
        reviewMapper.updateById(review);
        
        // 3. 方法正常返回 → Spring AMQP 自动 ACK → 消息从队列删除
        
    } catch (Exception e) {
        // 4. 抛出异常 → Spring AMQP 自动 NACK → 触发重试机制
        throw new RuntimeException("AI审核失败", e);
    }
}
```

**关键点**：
- ✅ **成功路径**：AI 调用成功 → 数据库更新成功 → 方法返回 → 自动 ACK
- ❌ **失败路径**：任何异常 → 抛出 RuntimeException → 自动 NACK → 重新入队

---

## 三、死信队列（Dead Letter Exchange, DLX）

### 3.1 什么是死信队列？

**死信**：无法被正常消费的消息，包括：
1. 消息被 NACK 且 `requeue=false`
2. 消息过期（TTL 超时）
3. 队列满了（达到 max-length）

**死信队列**：专门存放死信的队列，用于兜底处理或人工介入。

### 3.2 项目中的 DLX 架构

```
┌─────────────────────────────────────────────────────────────┐
│                      正常业务流程                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
        生产者发送消息到 review.exchange
                              ↓
                    路由键：review.audit
                              ↓
                  ┌─────────────────────┐
                  │ review.audit.queue  │ ← 配置了 DLX 参数
                  └─────────────────────┘
                              ↓
                  消费者处理（AUTO 模式）
                              ↓
                    ┌─────────┴─────────┐
                    │                   │
                 成功 ACK            失败 NACK
                    │                   │
                消息删除          Spring AMQP 重试 3 次
                                        │
                                  3 次全部失败
                                        ↓
┌─────────────────────────────────────────────────────────────┐
│                      死信流程                                │
└─────────────────────────────────────────────────────────────┘
                                        ↓
                消息路由到 review.dlx.exchange
                                        ↓
                    路由键：review.audit.dlx
                                        ↓
                  ┌─────────────────────────┐
                  │ review.audit.dlx.queue  │
                  └─────────────────────────┘
                                        ↓
                  handleFailedReview() 兜底处理
                                        ↓
                  标记为 AI_ERROR + 人工审核
```

### 3.3 配置代码详解

**步骤 1：定义死信交换机和死信队列**
```java
// 死信交换机
@Bean
public DirectExchange reviewDlxExchange(){
    return new DirectExchange("review.dlx.exchange");
}

// 死信队列
@Bean
public Queue reviewAuditDlxQueue(){
    return QueueBuilder.durable("review.audit.dlx.queue").build();
}

// 绑定死信队列到死信交换机
@Bean
public Binding reviewAuditDlxBinding(){
    return BindingBuilder
            .bind(reviewAuditDlxQueue())
            .to(reviewDlxExchange())
            .with("review.audit.dlx");
}
```

**步骤 2：正常队列配置 DLX 参数**
```java
@Bean
public Queue reviewAuditQueue(){
    return QueueBuilder.durable("review.audit.queue")
        // 关键配置：指定死信交换机
        .withArgument("x-dead-letter-exchange", "review.dlx.exchange")
        // 关键配置：指定死信路由键
        .withArgument("x-dead-letter-routing-key", "review.audit.dlx")
        .build();
}
```

**步骤 3：死信队列的消费者（兜底处理）**
```java
@RabbitListener(queues = "review.audit.dlx.queue")
public void handleFailedReview(ReviewAuditMessage message){
    log.error("AI审核失败，转人工审核,reviewId={}", message.getReviewId());
    
    // 降级处理：标记为需要人工审核
    Review review = new Review();
    review.setId(message.getReviewId());
    review.setAiVerdict("AI_ERROR");
    review.setStatus(ReviewStatus.PENDING.getCode()); // 0-待审核
    reviewMapper.updateById(review);
}
```

---

## 四、完整消息流转图

```
用户提交评论
    ↓
ReviewServiceImpl.addReview()
    ↓
rabbitTemplate.convertAndSend("review.exchange", "review.audit", message)
    ↓
消息进入 review.audit.queue
    ↓
ReviewAuditConsumer.handleReviewAudit() 消费
    ↓
    ├─ 成功：AI 返回 APPROVE/REJECT/MANUAL_REVIEW
    │         ↓
    │    更新数据库 status 字段
    │         ↓
    │    方法正常返回 → 自动 ACK → 消息删除 ✅
    │
    └─ 失败：AI 调用超时/网络异常/模型错误
              ↓
         throw RuntimeException → 自动 NACK
              ↓
         Spring AMQP 重试（默认 3 次）
              ↓
         3 次全败 → 消息成为死信
              ↓
         路由到 review.dlx.exchange
              ↓
         进入 review.audit.dlx.queue
              ↓
         handleFailedReview() 兜底处理
              ↓
         标记为 AI_ERROR + 人工审核 ⚠️
```

---

## 五、面试高频问题与回答模板

### Q1：你们项目为什么要用消息队列？

**回答要点**：
1. **业务背景**：AI 审核评论耗时 1~3 秒，同步调用影响用户体验
2. **解决方案**：异步处理 + 立即返回"审核中"状态
3. **技术选型**：RabbitMQ 支持消息持久化、死信队列等可靠性机制

### Q2：如何保证消息不丢失？

**三层保障**：
1. **生产者确认**：可配置 `publisher-confirms` 确保消息到达交换机
2. **消息持久化**：队列和消息都设置为 `durable`
3. **消费者确认**：使用 AUTO/MANUAL 模式，处理成功才 ACK

**项目实现**：
- 队列持久化：`QueueBuilder.durable("review.audit.queue")`
- 消费者 AUTO 确认：处理成功才自动 ACK，失败自动 NACK 重试

### Q3：消息重复消费怎么办？

**原因**：
- 消费者处理完业务逻辑，但在 ACK 之前宕机
- RabbitMQ 认为消息未消费，重新投递

**解决方案**：
1. **业务幂等**：同一条消息处理多次结果一致
2. **去重表**：Redis/MySQL 记录已处理的消息 ID

**项目实现**：
- `reviewMapper.updateById(review)` 是覆盖更新，天然幂等
- 如果是扣库存等操作，需要先查状态再操作

### Q4：死信队列的作用是什么？

**核心作用**：
1. **兜底处理**：消息多次重试失败后，不能直接丢弃
2. **人工介入**：转入死信队列后，可以人工排查问题
3. **降级策略**：AI 审核失败 → 降级为人工审核

**项目实现**：
- 正常队列配置 DLX 参数
- 死信队列消费者标记为"AI_ERROR"，等待人工处理

### Q5：AUTO 模式下消息会丢失吗？JVM 崩溃怎么办？

**误区澄清**：很多人以为 AUTO 模式是"消息投递给消费者就立即 ACK"，这是错误的。

Spring AMQP 的三种模式：

| 模式 | 触发时机 | 风险 |
|---|---|---|
| `none` | 消息发出就 ACK（真正的投递即确认） | 消息可能丢失 |
| `auto` | 方法正常返回→ACK，抛异常→NACK | 安全，大多数场景适用 |
| `manual` | 手动调用 `channel.basicAck/basicNack` | 最安全，代码复杂 |

**AUTO 模式的实际行为**：
- 消息到达消费者，RabbitMQ 标记为 `unacked`（从队列移出，但尚未确认）
- Spring AMQP 调用处理方法：成功返回→自动 ACK，抛异常→本地重试→重试耗尽→自动 NACK→进死信
- **JVM 崩溃？** RabbitMQ 检测到连接断开→自动把所有 `unacked` 消息重新入队

**结论**：AUTO 模式下消息不会丢失，`none` 模式才会丢失。

### Q6：如果 AI 服务长时间不可用怎么办？

**当前问题**：
- 消息会不断重试→堆积在队列中
- 死信队列也会堆积大量消息

**优化方案**：
1. **限流熔断**：接入 Sentinel，AI 服务熔断后直接降级
2. **延迟队列**：失败后延迟 5 分钟再重试，避免瞬时压力（可选，当前项目暂不需要）
3. **监控告警**：队列堆积超过阈值时触发告警

### Q7：ConfirmCallback 和 ReturnsCallback 有什么区别？

**两个回调触发场景不同**：

| 回调 | 触发时机 | 说明 |
|---|---|---|
| `ConfirmCallback` (ack=true) | 消息成功到达交换机 | 正常流程 |
| `ConfirmCallback` (ack=false) | 消息**未到达交换机** | 网络故障、Broker 宕机 |
| `ReturnsCallback` | 消息到了交换机，但**路由不到队列** | routing key 写错、队列未创建 |

**关键点**：
- ConfirmCallback ack=false：消息连交换机都没到，需要重发
- ReturnsCallback：消息到了交换机，但交换机找不到匹配的队列

**必须开启 `mandatory=true`**，否则路由失败的消息会被 Broker 直接丢弃。

**项目实现**：
```java
rabbitTemplate.setMandatory(true);

// 交换机确认
rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
    if (ack) {
        redisTemplate.delete(Constants.RABBITMQ_CORRELATION_MSG_ID + msgId);
    } else {
        handleMessageRetry(msgId, redisKey, rabbitTemplate);
    }
});

// 路由失败回退
rabbitTemplate.setReturnsCallback(returned -> {
    handleMessageRetry(msgId, redisKey, rabbitTemplate);
});
```

### Q8：如何保证生产者消息不丢失？Redis 在这里起什么作用？

**问题背景**：ConfirmCallback 是异步回调，如果 Broker 宕机，回调可能永远不触发，消息就丢了。

**方案：发送前持久化到 Redis**

```
生产者发送流程：
1. 生成唯一 msgId
2. 将消息体和重试次数存入 Redis Hash（TTL = 10分钟）
   key: mq:msgId:{msgId}  →  {message: \"...\", retryCount: 0}
3. 发送消息到 RabbitMQ，带上 CorrelationData(msgId)
4. ConfirmCallback 回调：
   - ack=true  → 删除 Redis 缓存（消息已安全到达）
   - ack=false → 从 Redis 取出消息，retryCount++，重新发送
5. 重试超过 MAX_RETRY_COUNT → 删除缓存，记录告警
```

**为什么用 Redis 而不直接在 ConfirmCallback 里重发**：
- ConfirmCallback 里拿不到原始消息对象，只有 correlationData
- Redis 作为消息的临时备份，确认成功后删除，失败时可以取出重发

**注意点**：
- Redis TTL 不要设太长（5~10分钟），避免内存堆积
- 重试计数从 Redis 取出时注意类型：Jackson 会把整数反序列化为 `Long`，不能强转 `Integer`

### Q9：事务与 MQ 消息发送的顺序问题怎么处理？

**问题场景**：
```java
@Transactional
public void createReview() {
    reviewMapper.insert(review);       // 写数据库
    rabbitTemplate.convertAndSend();   // 发 MQ
}
```

**两种风险**：
1. insert 成功，convertAndSend 抛异常 → 事务回滚，数据库无数据，但消息可能已发出
2. convertAndSend 成功，事务后续代码抛异常 → 事务回滚，消息已发出但数据库没记录

**解决方案：`@TransactionalEventListener`**

在事务提交后再发消息：

```java
// 1. Service 里只写库、发布事件
@Transactional
public void createReview() {
    reviewMapper.insert(review);
    eventPublisher.publishEvent(new ReviewCreatedEvent(message, msgId));
}

// 2. 监听器在事务提交后才发 MQ
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handleReviewCreated(ReviewCreatedEvent event) {
    rabbitTemplate.convertAndSend("review.exchange", "review.audit", 
        event.getMessage(), new CorrelationData(event.getMsgId()));
}
```

**核心原理**：`AFTER_COMMIT` 阶段在事务成功提交后才触发，事务回滚则事件不触发。

---

## 六、项目亮点总结

### 技术亮点
1. ✅ **异步解耦**：AI 审核与用户请求解耦，提升响应速度
2. ✅ **消费者可靠性**：AUTO 模式 + 死信队列 + 重试机制，JVM 崩溃也不丢消息
3. ✅ **生产者可靠性**：Redis 备份 + ConfirmCallback/ReturnsCallback 双重确认
4. ✅ **事务安全**：@TransactionalEventListener 保证事务提交后再发 MQ
5. ✅ **降级策略**：AI 失败自动转人工审核，保证业务连续性

### 可优化点（面试加分项）
1. 🔧 **幂等性增强**：引入消息去重表（Redis）
2. 🔧 **延迟重试**：使用延迟队列避免瞬时重试
3. 🔧 **监控告警**：接入 Prometheus + Grafana 监控队列堆积

---

## 七、关键代码速查

### 发送消息（生产者）
```java
// ReviewServiceImpl.java
rabbitTemplate.convertAndSend(
    "review.exchange",      // 交换机
    "review.audit",         // 路由键
    new ReviewAuditMessage(reviewId, content, rating)
);
```

### 消费消息（消费者）
```java
// ReviewAuditConsumer.java
@RabbitListener(queues = "review.audit.queue")
public void handleReviewAudit(ReviewAuditMessage message){
    try {
        // 业务处理
    } catch (Exception e) {
        throw new RuntimeException("AI审核失败", e); // 触发 NACK
    }
}
```

### 配置死信队列
```java
// RabbitMQConfig.java
@Bean
public Queue reviewAuditQueue(){
    return QueueBuilder.durable("review.audit.queue")
        .withArgument("x-dead-letter-exchange", "review.dlx.exchange")
        .withArgument("x-dead-letter-routing-key", "review.audit.dlx")
        .build();
}
```

---

## 八、面试话术模板

**开场**：
> "我们项目中用 RabbitMQ 实现了 AI 评论审核的异步处理。用户提交评论后，系统立即返回'审核中'状态，后台通过消息队列异步调用通义千问进行内容审核。"

**深入技术**：
> "为了保证消息可靠性，我们做了生产者和消费者两端的保障。消费者端使用 Spring AMQP 的 AUTO 确认模式，处理成功自动 ACK，失败自动 NACK 并重试 3 次，重试失败后路由到死信队列兜底。生产者端使用 ConfirmCallback 和 ReturnsCallback 双重确认机制，发送前先把消息备份到 Redis，确认成功后删除，失败则从 Redis 取出重试。另外，为了避免事务回滚导致的消息不一致，我们用 @TransactionalEventListener 在事务提交后才发送 MQ。"

**亮点总结**：
> "这套方案覆盖了消息队列的完整生命周期：生产者可靠性（Redis备份+确认回调）、消费者可靠性（AUTO模式+死信队列）、事务安全（事件监听器），以及降级策略（AI失败转人工），是一个比较完整的企业级消息队列实践。"

---

**最后提醒**：面试时不要死记硬背，结合自己的理解和项目实际情况灵活表达！
