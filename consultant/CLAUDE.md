---
description: 
alwaysApply: true
---

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 **Spring Boot 3.5.x** 和 **LangChain4j** 的 **智能美食推荐与餐厅管理系统**。使用阿里云 **通义千问（DashScope）** 作为大语言模型与嵌入模型，结合 **RAG**、本地 **Function Calling**（`@Tool`）以及 **智谱 MCP 联网搜索**（可选），提供智能美食咨询、餐厅预订、用户管理、订单管理等能力。

**技术栈**: Spring Boot 3.5.0 + LangChain4j 1.1.x + MyBatis-Plus 3.5.x + 通义千问（DashScope Starter）+ Redis + Redisson + MySQL + RabbitMQ + Elasticsearch + Hutool + Prometheus；前端为 `static/index.html`（Vue 3 单页）。

**项目结构**: 应用代码位于 `consultant/` 子目录下；数据库初始化脚本位于 `consultant/sql/`（若仓库另有顶层 `sql/`，以实际路径为准）。

**核心特性**:
- **AI 智能推荐**: 基于用户订单历史和评价偏好的个性化餐厅推荐
- **RAG 知识库**: 美食知识向量检索增强生成
- **MCP 联网搜索**: 智谱 BigModel 实时网络搜索能力
- **AI 评价审核**: 自动识别违规评价内容（RabbitMQ 异步处理）
- **热门榜单**: 基于近30天订单数的餐厅热度排行（Redis ZSet）
- **全文搜索**: Elasticsearch 餐厅搜索与索引管理
- **分布式锁**: Redisson 实现订单号生成、缓存预热等场景
- **缓存策略**: 封装 CacheClient，支持缓存穿透、击穿防护
- **消息去重**: RabbitMQ 消息幂等性保障（Redis 去重 + 重试机制）
- **延时任务**: Redis ZSet 实现订单超时自动取消

## 构建和运行命令

**注意**: 所有命令需要在 `consultant/` 目录下执行。

```bash
cd consultant

mvn clean compile
mvn test
mvn test -Dtest=ClassName
mvn test -Dtest=ClassName#methodName
mvn clean package
mvn spring-boot:run
java -jar target/consultant-0.0.1-SNAPSHOT.jar
```

**密钥与配置**: 模型与嵌入使用 `langchain4j.community.dashscope`，需在激活的 Spring Profile 中配置 **`dashscope.api-key`**（见 `application-work.yaml` / `application-home.yaml`）。智谱 **MCP 联网搜索** 需配置 **`bigmodel.api-key`**（见 `McpConfig` 与 `application.yaml`）。

**访问地址**:

- 前端界面: `http://localhost:8080/index.html`
- AI 聊天接口: **`GET /ai/chat?memoryId={会话ID}&message={消息内容}`**
- 用户注册: `POST /api/user/register`
- 用户登录: `POST /api/user/login`
- 餐厅分页查询: `GET /api/restaurant/page`
- 餐厅搜索（ES）: `GET /api/restaurant/search`
- 热门榜单: `GET /api/hot-rank/restaurants`
- 评价管理: `POST /api/review`
- 订单管理: `POST /api/order`
- Prometheus 监控: `http://localhost:8080/actuator/prometheus`
- ES 索引管理: `POST /api/admin/es/restaurant/rebuild`（管理员）

## 运行前置条件

1. **Java 17**

2. **MySQL**（默认 profile `dev` 下为端口 **3306**）
   - 数据库名: `food_ai_system`
   - 用户名/密码见 `application.yaml` 与各 profile
   - 执行 `consultant/sql/` 下脚本初始化（如 `all_in_one.sql`）

3. **Redis**（默认 **6379**）
   - 聊天记忆：`chat:memory:{memoryId}`
   - 向量索引：`food-knowledge-index`（RAG）
   - 知识库一次性初始化标记：`knowledge:initialized`
   - JWT：`user:token:{userId}`（见 `Constants`）
   - RabbitMQ 消息去重：`mq:consumed:{msgId}`（72小时过期）
   - RabbitMQ 消息重试：`mq:msgId:{msgId}`（存储重试次数与消息元数据）
   - 热门榜单：`hot:restaurant:rank`（ZSet，按订单数排序）
   - 热门榜单详情缓存：`hot:restaurant:detail:{id}`（40天过期）
   - 订单超时延时队列：`order:no_show:delay:zset`（ZSet 实现延时任务）
   - 餐厅摘要缓存：`restaurant:ai:summary:{id}`（24小时过期）
   - 用户信息缓存：`user:info:{id}`（30分钟过期）
   - 餐厅信息缓存：`restaurant:info:{id}`（72小时过期）
   - 评价详情缓存：`review:detail:{id}`
   - 订单详情缓存：`order:detail:{id}`

4. **RabbitMQ**（默认 **localhost:5672**）
   - 交换机：`review.exchange`（评价审核）、`review.dlx.exchange`（死信）、`review.rating.exchange`（评分更新）、`order.exchange`（订单事件）
   - 队列：`review.audit.queue`（审核队列）、`review.audit.dlx.queue`（死信队列）、`review.rating.queue`（评分更新队列）、`order.confirm.timeout.queue`（订单确认超时队列）
   - 用户名/密码见 `application.yaml`（默认 admin/admin123）

5. **Elasticsearch**（默认 **localhost:9200**）
   - 索引名：`restaurant`
   - 用于餐厅全文搜索（名称、地址、菜系）
   - 首次启动需执行索引初始化（`RestaurantIndexInitializer` 自动执行）

6. **API 密钥**
   - **DashScope**：`dashscope.api-key`，用于 `qwen-plus`、`text-embedding-v4` 等
   - **智谱 BigModel**（启用 MCP 搜索时）：`bigmodel.api-key`，用于 `https://open.bigmodel.cn/api/mcp/web_search/sse`

## 核心架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│ 前端层                                                       │
│  - static/index.html（AI 聊天等）                            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ Controller 层                                                │
│  - cn.zjw.ai.ChatController（/ai/chat）                      │
│  - UserController / RestaurantController / OrderController   │
│  - ReviewController / DishController / HotRankController     │
│  - RestaurantIndexAdminController（ES 索引管理）             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ AI 组装层                                                    │
│  - AiServiceFactory：AiServices.builder 注册 AiHelperService │
│  - RagConfig / KnowledgeBaseInitializer                      │
│  - McpConfig（McpToolProvider → 智谱 web_search）             │
│  - ReviewAnalysisService（AI 评价审核）                      │
│  - RestaurantSummaryService（AI 餐厅摘要生成）               │
│  - FoodReservationTool（预订工具）                           │
│  - RecommendationTool（个性化推荐工具）                      │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 消息队列层（RabbitMQ）                                        │
│  - ReviewAuditConsumer（消费评价审核消息）                    │
│  - ReviewRatingUpdateConsumer（消费评分更新消息）            │
│  - OrderConfirmTimeoutConsumer（消费订单超时消息）           │
│  - OrderEventListener / ReviewEventListener（事件监听）      │
│  - MqDedupService（消息去重服务）                            │
│  - RabbitMQConfig（消息确认 + 重试 + 死信队列）               │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 搜索层（Elasticsearch）                                       │
│  - RestaurantIndexService（索引管理）                        │
│  - RestaurantSearchRepository（搜索仓库）                    │
│  - RestaurantIndexInitializer（启动时自动初始化索引）        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ Service / Mapper / 数据访问                                  │
│  - UserService、RestaurantService、OrderService 等            │
│  - HotRankService（热门榜单）                                │
│  - RatingUpdateService（评分更新）                           │
│  - OrderDelayService（订单延时任务）                         │
│  - CacheClient（缓存客户端，封装穿透/击穿防护）               │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 定时任务层                                                   │
│  - HotRankScheduler（每日凌晨2点刷新热门榜单）               │
│  - OrderNoShowCancelTask（每5秒扫描订单超时取消）            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 数据存储                                                     │
│  - MySQL：业务数据                                           │
│  - Redis：记忆 + 向量 + Token + 知识库初始化标记 + MQ去重    │
│           + 热门榜单 + 延时队列 + 缓存                       │
│  - Elasticsearch：餐厅搜索索引                               │
└─────────────────────────────────────────────────────────────┘
```

### AI 聊天数据流（与实现一致）

```
用户请求 GET /ai/chat?memoryId=&message=
    ↓
ChatController → AiHelperService.chat()
    ↓
LangChain4j AiServices 代理
    ├─ MessageWindowChatMemory + RedisChatMemoryStore（按 memoryId）
    ├─ ContentRetriever（RedisEmbeddingStore RAG）
    ├─ FoodReservationTool（@Tool → MySQL restaurant_reservation）
    └─ McpToolProvider（智谱 MCP：webSearch* 等，由模型按需调用）
    ↓
通义千问 ChatModel（DashScope）
    ↓
返回 String → CommonResult.success（当前为非流式整段回复）
```

## AI 模块说明

### 1. `AiHelperService` + `AiServiceFactory`

- 接口 **`cn.zjw.ai.AiHelperService`**：`@SystemMessage(fromResource = "system.txt")`，方法 `String chat(@MemoryId int memoryId, @UserMessage String message)`。
- 接口上的 **`@AiService` 已注释**；由 **`AiServiceFactory`** 使用 **`AiServices.builder(AiHelperService.class)`** 手动构建 Bean，便于同时挂载 **本地工具** 与 **McpToolProvider**。
- 注册内容要点：
  - `chatModel`：`ChatModel`（Starter 提供的 `qwenChatModel`）
  - `contentRetriever`：RAG
  - `tools(foodReservationTool)`
  - `toolProvider(mcpToolProvider)`
  - `chatMemoryProvider`：`MessageWindowChatMemory`，**`maxMessages(10)`**，`RedisChatMemoryStore`

### 2. RAG：`RagConfig` + `KnowledgeBaseInitializer`

- **`RagConfig`**
  - `EmbeddingStore<TextSegment>`：`RedisEmbeddingStore`，索引名 **`food-knowledge-index`**，维度 **1536**。
  - **`EmbeddingStoreContentRetriever`**：**`maxResults(5)`**，**`minScore(0.75)`**。
- **`KnowledgeBaseInitializer`**（`@PostConstruct`）
  - 若 Redis 中已有 **`knowledge:initialized`**，则**跳过**向量化。
  - 否则加载 `classpath:content/*.txt`，**`DocumentSplitters.recursive(500, 100)`**，写入向量库并设置初始化标记（长 TTL）。

### 3. 会话记忆：`RedisChatMemoryStore`

- Key 前缀：**`chat:memory:{memoryId}`**。
- 使用 LangChain4j **`ChatMessageSerializer` / `ChatMessageDeserializer`** 存取 JSON；含工具调用时，会序列化 **tool 请求与结果**，体积可能较大。
- TTL：**1 天**；含 **`fixMessageOrder`**，避免首条非系统消息顺序异常。

### 3. 本地工具：`FoodReservationTool` + `RecommendationTool`

- **`FoodReservationTool`**（包路径：**`cn.zjw.ai.tools`**）
  - 表名：**`restaurant_reservation`**（实体 `Reservation`）
  - 方法：`reserveRestaurant`、`findReservation`（`@Tool`）

- **`RecommendationTool`**（包路径：**`cn.zjw.ai.tools`**）
  - **`getUserOrderHistory()`**：查询用户订单历史，分析常点菜系（返回 JSON 统计）
  - **`getTopRatedRestaurants(category, minRating, limit)`**：查询高分餐厅列表，支持菜系、评分筛选
  - **`getUserReviews(limit)`**：查询用户评价历史，分析口味偏好
  - 用途：为 AI 提供个性化推荐的数据支持

### 4. MCP 联网搜索：`McpConfig`

- **`McpToolProvider`** + **`DefaultMcpClient`** + **`HttpMcpTransport`**（智谱 **`web_search` SSE** URL，需 **`bigmodel.api-key`**）。
- 工具由远端 MCP 下发（如 `webSearchPro`、`webSearchSogou` 等）；是否调用由**模型决定**，并非每条用户消息都会执行搜索。
- SSE 长连接在空闲时可能出现 **读超时与自动重连**（日志中可见），属传输层常见现象。

### 5. `ChatController`

- 映射：**`/ai/chat`**（类上 **`@RequestMapping("/ai")`**）。
- 返回 **`CommonResult<String>`**，**非流式**（非 `Flux` / SSE）。

### 6. AI 评价审核：`ReviewAnalysisService` + `ReviewAuditConsumer`

- **业务流程**：
  1. 用户提交评价 → `ReviewServiceImpl.addReview()` 保存评价（状态：待审核）
  2. 发送消息到 RabbitMQ（`review.exchange` → `review.audit.queue`）
  3. `ReviewAuditConsumer.handleReviewAudit()` 消费消息，调用 AI 审核
  4. `ReviewAnalysisService.analyzeReview()` 使用通义千问分析评价内容
  5. 返回审核结果：`APPROVE`（通过）、`REJECT`（拒绝）、`MANUAL_REVIEW`（人工审核）
  6. 更新数据库评价状态和 AI 标签

- **消息可靠性保障**：
  - 生产者确认：`ConfirmCallback`（交换机确认）+ `ReturnsCallback`（路由失败回退）
  - 消息重试：失败消息存入 Redis，最多重试 3 次
  - 死信队列：重试失败后路由到 `review.audit.dlx.queue`，转人工审核

- **核心类**：
  - `cn.zjw.ai.service.ReviewAnalysisService`：AI 审核接口（`@AiService`）
  - `cn.zjw.mq.consumer.ReviewAuditConsumer`：消息消费者
  - `cn.zjw.config.RabbitMQConfig`：RabbitMQ 配置（交换机、队列、绑定、回调）

### 7. AI 餐厅摘要生成：`RestaurantSummaryService`

- **业务流程**：
  1. 用户查询餐厅详情时，检查 Redis 缓存（`restaurant:ai:summary:{id}`）
  2. 缓存未命中时，调用 AI 生成餐厅口碑摘要
  3. `RestaurantSummaryService.generateSummary()` 使用通义千问分析餐厅评价
  4. 返回结构化摘要（优点、缺点、推荐理由）
  5. 结果缓存到 Redis（24小时过期）

- **核心类**：
  - `cn.zjw.ai.service.RestaurantSummaryService`：AI 摘要生成接口（`@AiService`）
  - `cn.zjw.ai.model.RestaurantSummary`：摘要结果模型

## 中间件集成说明

### 1. Elasticsearch 搜索功能

- **索引名称**：`restaurant`
- **文档模型**：`RestaurantEsDoc`（包含 id、name、address、category、rating 等字段）
- **核心类**：
  - `cn.zjw.search.service.RestaurantIndexService`：索引管理服务
  - `cn.zjw.search.repository.RestaurantSearchRepository`：搜索仓库（Spring Data ES）
  - `cn.zjw.search.init.RestaurantIndexInitializer`：启动时自动初始化索引
  - `cn.zjw.controller.admin.RestaurantIndexAdminController`：管理员索引管理接口

- **功能**：
  - 全文搜索：支持餐厅名称、地址、菜系模糊搜索
  - 索引管理：创建、删除、重建索引
  - 数据同步：MySQL 数据变更自动同步到 ES
  - 启动初始化：首次启动自动创建索引并导入数据

- **管理接口**：
  - `POST /api/admin/es/restaurant/rebuild`：重建索引（删除 → 创建 → 全量导入）
  - `POST /api/admin/es/restaurant/import`：仅全量导入（不删索引）
  - `DELETE /api/admin/es/restaurant`：删除索引

### 2. Redis 缓存策略（CacheClient）

- **核心类**：`cn.zjw.common.cache.CacheClient`
- **功能**：
  - **缓存穿透防护**：空值缓存（2分钟过期）
  - **缓存击穿防护**：互斥锁机制（`setIfAbsent`）
  - **逻辑过期**：支持逻辑过期时间，防止缓存雪崩
  - **泛型支持**：自动 JSON 序列化/反序列化

- **使用场景**：
  - 用户信息缓存（30分钟）
  - 餐厅信息缓存（72小时）
  - 餐厅摘要缓存（24小时）
  - 评价详情缓存
  - 订单详情缓存

### 3. Redisson 分布式锁

- **核心类**：`cn.zjw.config.RedissonConfig`
- **使用场景**：
  - **订单号生成**：`order:generate:lock`（防止并发生成重复订单号）
  - **缓存预热**：`lock:restaurant:{id}`（防止缓存击穿）
  - **订单超时处理**：`order:no_show:lock:{orderId}`（防止重复取消）

- **订单号生成策略**：
  - 格式：`ORDER + yyyyMMdd + 6位序列号`
  - 使用 Redisson 分布式锁保证并发安全
  - Redis 存储每日序列号（`order:seq:yyyyMMdd`）

### 4. 热门榜单（Redis ZSet）

- **核心类**：
  - `cn.zjw.service.HotRankService`：热门榜单服务
  - `cn.zjw.task.HotRankScheduler`：定时刷新任务（每日凌晨2点）
  - `cn.zjw.controller.user.HotRankController`：榜单查询接口

- **实现原理**：
  - 统计近30天已支付订单数，按餐厅分组
  - 结果存入 Redis ZSet（`hot:restaurant:rank`），score 为订单数
  - 榜单详情缓存到 Redis Hash（`hot:restaurant:detail:{id}`，40天过期）
  - 支持手动刷新和定时自动刷新

- **接口**：
  - `GET /api/hot-rank/restaurants?limit=10`：获取热门餐厅列表
  - `POST /api/hot-rank/refresh`：手动刷新榜单

### 5. 订单延时任务（Redis ZSet）

- **核心类**：
  - `cn.zjw.service.OrderDelayService`：延时任务服务
  - `cn.zjw.task.OrderNoShowCancelTask`：定时扫描任务（每5秒）

- **实现原理**：
  - 订单创建后，将订单ID和超时时间存入 Redis ZSet（`order:no_show:delay:zset`）
  - 定时任务每5秒扫描到期订单（score < 当前时间）
  - 使用 Redisson 分布式锁防止重复处理
  - 超时订单自动取消（状态变更为 CANCELLED）

- **超时规则**：
  - 用餐时间后30分钟未到店，自动取消订单

### 6. RabbitMQ 消息去重与重试

- **核心类**：
  - `cn.zjw.mq.support.MqDedupService`：消息去重服务
  - `cn.zjw.config.RabbitMQConfig`：消息确认与重试配置

- **去重机制**：
  - 消费者接收消息时，检查 Redis 是否存在消息ID（`mq:consumed:{msgId}`）
  - 已消费消息跳过处理，未消费消息标记为已消费（72小时过期）

- **重试机制**：
  - 生产者发送消息时，存储消息元数据到 Redis（`mq:msgId:{msgId}`）
  - 发送失败时，自动重试（最多3次，间隔5分钟）
  - 重试失败后，消息路由到死信队列（`review.audit.dlx.queue`）

- **消息确认**：
  - `ConfirmCallback`：确认消息是否到达交换机
  - `ReturnsCallback`：处理路由失败的消息

### 7. 监控与可观测性（Prometheus）

- **核心配置**：`management.endpoints.web.exposure.include=prometheus`
- **监控端点**：`http://localhost:8080/actuator/prometheus`
- **监控指标**：
  - JVM 内存、线程、GC
  - HTTP 请求统计
  - 数据库连接池
  - Redis 连接池
  - 自定义业务指标（缓存命中率等）

- **集成方式**：
  - 添加 `micrometer-registry-prometheus` 依赖
  - Prometheus 定期拉取 `/actuator/prometheus` 端点数据
  - Grafana 可视化展示

## 配置说明

### DashScope（`application.yaml`）

```yaml
langchain4j:
  community:
    dashscope:
      chat-model:
        api-key: ${dashscope.api-key}
        model-name: qwen-plus
        temperature: 0.7
        timeout: 30s
      streaming-chat-model:
        api-key: ${dashscope.api-key}
        model-name: qwen-plus
        temperature: 0.7
      embedding-model:
        api-key: ${dashscope.api-key}
        model-name: text-embedding-v4
```

- Profile（如 `application-dev.yaml`）中设置 **`dashscope.api-key`**。
- **`spring.profiles.active`** 默认为 **`dev`**（可按环境修改）。

### 智谱 MCP（`application.yaml`）

```yaml
bigmodel:
  api-key: ${bigmodel.api-key}
```

- 各环境在 profile 中设置 **`bigmodel.api-key`**。

### 数据库与 Redis

- MySQL URL 在各 profile 的 `spring.datasource.url` 中（默认 `localhost:3306`）。
- Redis：`spring.data.redis.host` / `port`（默认 `localhost:6379`）。

### Elasticsearch（`application.yaml`）

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
    connection-timeout: 5s
```

- 各环境在 profile 中配置 ES 地址。

### RabbitMQ（`application.yaml`）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin123
    publisher-confirm-type: correlated  # 开启发布确认
    publisher-returns: true              # 开启路由失败回退
    listener:
      simple:
        acknowledge-mode: auto           # 自动确认消息
        retry:
          enabled: true
          max-attempts: 3                # 最大重试3次
          delay: 1000ms
```

- 消息确认机制：生产者发送消息后，通过 `ConfirmCallback` 和 `ReturnsCallback` 确认消息是否成功到达交换机和队列。
- 消息重试：失败消息存入 Redis（`mq:msgId:{msgId}`），最多重试 3 次（见 `Constants.MAX_RETRY_COUNT`）。
- 死信队列：重试失败后，消息路由到死信队列，由专门的消费者处理。

### JWT

- **`jwt.secret`**、**`jwt.expire-hours`**（如 720）、**`jwt.header`**：`Authorization: Bearer <token>`。
- 登录拦截仅拦截 **`/api/**`**（见 `WebMvcConfig`）；**`/ai/chat` 不要求 JWT**。

### Prometheus 监控

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  prometheus:
    metrics:
      export:
        enabled: true
  metrics:
    tags:
      application: consultant
```

## 项目结构（Java 包，与当前代码一致）

```
consultant/
├── src/main/java/cn/zjw/
│   ├── ConsultantApplication.java
│   ├── ai/
│   │   ├── AiHelperService.java          # AI 接口声明
│   │   ├── AiServiceFactory.java         # AiServices 手动装配
│   │   ├── ChatController.java           # GET /ai/chat
│   │   ├── mcp/McpConfig.java            # 智谱 MCP Web Search
│   │   ├── rag/RagConfig.java            # EmbeddingStore + ContentRetriever
│   │   ├── rag/KnowledgeBaseInitializer.java
│   │   ├── repository/RedisChatMemoryStore.java
│   │   ├── service/ReviewAnalysisService.java  # AI 评价审核
│   │   ├── service/RestaurantSummaryService.java  # AI 餐厅摘要
│   │   ├── model/ReviewAnalysisResult.java
│   │   ├── model/RestaurantSummary.java
│   │   └── tools/
│   │       ├── FoodReservationTool.java  # 预订工具
│   │       └── RecommendationTool.java   # 个性化推荐工具
│   ├── common/
│   │   ├── cache/CacheClient.java        # 缓存客户端（穿透/击穿防护）
│   │   ├── constant/Constants.java       # 常量定义
│   │   ├── context/UserContext.java      # 用户上下文（ThreadLocal）
│   │   ├── enums/                        # 枚举类
│   │   ├── exception/                    # 自定义异常
│   │   ├── model/PageParams.java         # 分页参数
│   │   ├── result/                       # 统一返回结果
│   │   └── utils/                        # 工具类
│   ├── config/
│   │   ├── WebMvcConfig.java             # MVC 配置（拦截器）
│   │   ├── MybatisPlusConfig.java        # MyBatis-Plus 配置
│   │   ├── RedisConfig.java              # Redis 配置
│   │   ├── RedissonConfig.java           # Redisson 配置
│   │   └── RabbitMQConfig.java           # RabbitMQ 配置
│   ├── controller/
│   │   ├── admin/
│   │   │   └── RestaurantIndexAdminController.java  # ES 索引管理
│   │   └── user/
│   │       ├── UserController.java
│   │       ├── RestaurantController.java
│   │       ├── OrderController.java
│   │       ├── ReviewController.java
│   │       ├── DishController.java
│   │       └── HotRankController.java    # 热门榜单
│   ├── mq/
│   │   ├── consumer/
│   │   │   ├── ReviewAuditConsumer.java  # 评价审核消费者
│   │   │   ├── ReviewRatingUpdateConsumer.java  # 评分更新消费者
│   │   │   └── OrderConfirmTimeoutConsumer.java  # 订单超时消费者
│   │   ├── listener/
│   │   │   ├── OrderEventListener.java   # 订单事件监听
│   │   │   └── ReviewEventListener.java  # 评价事件监听
│   │   ├── event/                        # 事件定义
│   │   ├── message/                      # 消息体定义
│   │   └── support/MqDedupService.java   # 消息去重服务
│   ├── search/
│   │   ├── document/RestaurantEsDoc.java # ES 文档模型
│   │   ├── repository/RestaurantSearchRepository.java
│   │   ├── service/RestaurantIndexService.java
│   │   └── init/RestaurantIndexInitializer.java  # 启动初始化
│   ├── service/
│   │   ├── UserService.java
│   │   ├── RestaurantService.java
│   │   ├── OrderService.java
│   │   ├── ReviewService.java
│   │   ├── DishService.java
│   │   ├── HotRankService.java           # 热门榜单服务
│   │   ├── RatingUpdateService.java      # 评分更新服务
│   │   ├── OrderDelayService.java        # 订单延时服务
│   │   └── impl/                         # 实现类
│   ├── task/
│   │   ├── HotRankScheduler.java         # 热门榜单定时任务
│   │   └── OrderNoShowCancelTask.java    # 订单超时取消任务
│   ├── mapper/                           # MyBatis Mapper 接口
│   ├── pojo/
│   │   ├── entity/                       # 实体类
│   │   ├── dto/                          # 数据传输对象
│   │   └── vo/                           # 视图对象
│   ├── handler/
│   │   ├── GlobalExceptionHandler.java   # 全局异常处理
│   │   └── MyMetaObjectHandler.java      # MyBatis-Plus 自动填充
│   └── interceptor/
│       └── AuthInterceptor.java          # JWT 认证拦截器
├── src/main/resources/
│   ├── application.yaml
│   ├── application-dev.yaml              # 开发环境配置
│   ├── system.txt                        # AI [REDACTED]
│   ├── content/                          # RAG 知识库 txt
│   └── static/index.html                 # 前端页面
├── sql/                                  # 数据库脚本
└── pom.xml
```

## 开发注意事项

### 首次启动

1. 执行 SQL 初始化（MySQL）。
2. 配置 **`dashscope.api-key`**；若使用 MCP，配置 **`bigmodel.api-key`**。
3. 启动 **Redis**（默认 6379）。
4. 启动 **RabbitMQ**（Docker 或本地安装）：`docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management`
5. 启动 **Elasticsearch**（默认 9200）：`docker run -d --name elasticsearch -p 9200:9200 -e "discovery.type=single-node" elasticsearch:8.11.0`
6. 首次启动会进行知识库向量化（若 Redis 无 `knowledge:initialized`），需数十秒级时间；后续启动会跳过。
7. 首次启动会自动创建 ES 索引并导入餐厅数据（`RestaurantIndexInitializer`）。
8. 若需**强制重建向量库**：删除 Redis 中 `knowledge:initialized` 及向量相关数据（或按运维要求清空对应索引）。
9. 若需**重建 ES 索引**：调用 `POST /api/admin/es/restaurant/rebuild` 接口。

### 修改 RAG 知识库

1. 编辑或新增 `src/main/resources/content/*.txt`。
2. 删除 Redis 键 **`knowledge:initialized`**（必要时清理向量索引键），重启应用以重新 ingest。

### 修改系统提示词

编辑 **`src/main/resources/system.txt`** 并重启。

### 添加本地 AI 工具

1. 在 **`cn.zjw.ai.tools`**（或统一包规范下）新增 `@Component` + `@Tool` 方法。
2. 在 **`AiServiceFactory`** 中 `.tools(yourToolBean)` 注册。

### 调整记忆窗口

在 **`AiServiceFactory`** 的 `MessageWindowChatMemory.builder()` 中修改 **`.maxMessages(10)`**。

### 调整 RAG 检索

在 **`RagConfig.contentRetriever`** 中修改 **`.maxResults`**、**`.minScore`**。

### 调试 RabbitMQ 消息流

1. **查看消息队列状态**：访问 RabbitMQ 管理界面 `http://localhost:15672`（默认用户名/密码：admin/admin123）
2. **查看消息去重缓存**：Redis 中查询 `mq:consumed:*` 键
3. **查看消息重试缓存**：Redis 中查询 `mq:msgId:*` 键
4. **查看死信队列**：检查 `review.audit.dlx.queue` 中是否有失败消息
5. **日志关键字**：搜索 `AI收到评价审核消息`、`AI审核评价结果`、`消息发送到交换机成功`

### 调试 Elasticsearch

1. **查看索引状态**：`GET http://localhost:9200/restaurant`
2. **查看文档数量**：`GET http://localhost:9200/restaurant/_count`
3. **测试搜索**：`GET http://localhost:9200/restaurant/_search?q=川菜`
4. **查看映射**：`GET http://localhost:9200/restaurant/_mapping`

### 调试热门榜单

1. **查看榜单数据**：Redis 中执行 `ZREVRANGE hot:restaurant:rank 0 -1 WITHSCORES`
2. **查看榜单缓存**：Redis 中查询 `hot:restaurant:detail:*` 键
3. **手动刷新榜单**：调用 `POST /api/hot-rank/refresh` 接口
4. **查看定时任务日志**：搜索 `开始刷新热门餐厅榜单`

### 调试订单延时任务

1. **查看延时队列**：Redis 中执行 `ZRANGE order:no_show:delay:zset 0 -1 WITHSCORES`
2. **查看分布式锁**：Redis 中查询 `order:no_show:lock:*` 键
3. **查看任务日志**：搜索 `开始扫描超时未到店订单`

### 性能优化建议

1. **缓存预热**：系统启动时，预热热门餐厅数据到 Redis
2. **批量操作**：使用 MyBatis-Plus 的批量插入/更新方法
3. **异步处理**：耗时操作（AI 审核、摘要生成）使用 RabbitMQ 异步处理
4. **索引优化**：为常用查询字段添加数据库索引
5. **连接池调优**：根据并发量调整数据库、Redis、ES 连接池大小

## 日志与调试

- 可将 `logging.level.dev.langchain4j` 等设为 `DEBUG` 查看模型与 RAG 行为（按需在 `application.yaml` 配置）。
- **MCP**：`McpConfig` 中可开关 **`logRequests` / `logResponses`**；SSE 超时重连会打 WARN，需与业务错误区分。
- **Elasticsearch**：可将 `logging.level.org.springframework.data.elasticsearch` 设为 `DEBUG` 查看查询语句。
- **RabbitMQ**：可将 `logging.level.org.springframework.amqp` 设为 `DEBUG` 查看消息流转。

## 常见问题排查

### 1. 知识库向量化失败

- **现象**：启动时卡在向量化步骤，或报错 `EmbeddingStore` 相关异常
- **排查**：
  - 检查 Redis 是否正常运行（`redis-cli ping`）
  - 检查 `dashscope.api-key` 是否配置正确
  - 检查 `content/` 目录下是否有知识库文件
  - 查看日志中是否有 API 调用失败信息
- **解决**：删除 Redis 中 `knowledge:initialized` 键，重启应用重新初始化

### 2. Elasticsearch 索引初始化失败

- **现象**：启动时报错 `ElasticsearchException`，或搜索功能不可用
- **排查**：
  - 检查 ES 是否正常运行（`curl http://localhost:9200`）
  - 检查 `application.yaml` 中 ES 地址配置
  - 查看日志中是否有连接超时或权限错误
- **解决**：调用 `POST /api/admin/es/restaurant/rebuild` 手动重建索引

### 3. RabbitMQ 消息堆积

- **现象**：队列中消息数量持续增长，消费者未消费
- **排查**：
  - 访问 RabbitMQ 管理界面查看队列状态
  - 检查消费者是否正常运行（查看日志）
  - 检查是否有消息消费异常（死信队列）
- **解决**：
  - 重启应用恢复消费者
  - 检查死信队列中的失败消息，修复后重新投递

### 4. 热门榜单数据为空

- **现象**：调用 `/api/hot-rank/restaurants` 返回空列表
- **排查**：
  - 检查 Redis 中是否有 `hot:restaurant:rank` 键
  - 检查数据库中是否有已支付订单数据
  - 查看定时任务日志是否执行成功
- **解决**：手动调用 `POST /api/hot-rank/refresh` 刷新榜单

### 5. 订单超时未自动取消

- **现象**：订单超时后状态未变更为 CANCELLED
- **排查**：
  - 检查 Redis 中 `order:no_show:delay:zset` 是否有数据
  - 检查定时任务日志是否正常执行
  - 检查 Redisson 分布式锁是否正常
- **解决**：
  - 检查 Redis 连接是否正常
  - 重启应用恢复定时任务

### 6. AI 工具调用失败

- **现象**：AI 聊天时工具未被调用，或返回错误信息
- **排查**：
  - 检查工具方法上是否有 `@Tool` 注解
  - 检查 `AiServiceFactory` 中是否注册了工具
  - 查看日志中是否有工具调用异常
  - 检查工具方法的参数类型是否正确
- **解决**：
  - 确保工具类是 `@Component`
  - 确保工具方法的 `@Tool` 注解有清晰的描述
  - 检查工具方法的返回值是否为 String 或 JSON

## 项目亮点总结（面试准备）

### 1. AI 技术深度集成

- **RAG 知识库**：使用 Redis 向量数据库实现美食知识检索增强
- **Function Calling**：实现餐厅预订、个性化推荐等本地工具调用
- **MCP 联网搜索**：集成智谱 BigModel 实时网络搜索能力
- **AI 审核与摘要**：自动审核评价内容、生成餐厅口碑摘要

### 2. 中间件技术栈完整

- **Elasticsearch**：全文搜索、索引管理、数据同步
- **RabbitMQ**：消息去重、重试机制、死信队列
- **Redis**：缓存策略（穿透/击穿防护）、热门榜单（ZSet）、延时队列
- **Redisson**：分布式锁、订单号生成

### 3. 系统设计亮点

- **缓存三级防护**：空值缓存（穿透）+ 互斥锁（击穿）+ 逻辑过期（雪崩）
- **消息可靠性保障**：生产者确认 + 消费者去重 + 自动重试 + 死信队列
- **延时任务实现**：Redis ZSet + 定时扫描 + 分布式锁
- **热门榜单设计**：定时统计 + ZSet 排序 + 缓存预热

### 4. 性能优化实践

- **批量操作**：MyBatis-Plus 批量插入/更新
- **异步处理**：耗时操作（AI 审核、摘要生成）使用 MQ 异步
- **缓存预热**：热门数据启动时预热到 Redis
- **监控体系**：Prometheus + Grafana 可观测性

## 技术难点与解决方案（面试准备）

### 1. 如何保证 RabbitMQ 消息不丢失？

- **生产者确认**：`publisher-confirm-type: correlated`，消息到达交换机后回调确认
- **消息持久化**：队列和消息都设置为持久化
- **消费者确认**：手动 ACK，处理成功后才确认
- **重试机制**：失败消息存入 Redis，最多重试 3 次
- **死信队列**：重试失败后路由到死信队列，人工介入

### 2. 如何防止缓存穿透、击穿、雪崩？

- **穿透**：空值缓存（2分钟过期），防止恶意查询不存在的数据
- **击穿**：互斥锁机制（`setIfAbsent`），同一时刻只有一个线程查询数据库
- **雪崩**：随机过期时间（基础时间 + 随机值），防止大量缓存同时失效

### 3. 如何实现订单超时自动取消？

- **Redis ZSet 延时队列**：订单创建时，将订单ID和超时时间存入 ZSet
- **定时扫描**：每5秒扫描到期订单（score < 当前时间）
- **分布式锁**：使用 Redisson 锁防止重复处理
- **状态变更**：超时订单自动取消，释放库存

### 4. 如何保证订单号全局唯一？

- **格式设计**：`ORDER + yyyyMMdd + 6位序列号`
- **分布式锁**：使用 Redisson 锁保证并发安全
- **每日重置**：Redis 存储每日序列号（`order:seq:yyyyMMdd`），次日自动重置

### 5. 如何实现热门榜单实时更新？

- **定时统计**：每日凌晨2点统计近30天订单数
- **ZSet 排序**：使用 Redis ZSet 存储榜单，score 为订单数
- **缓存预热**：榜单详情缓存到 Redis（40天过期）
- **手动刷新**：提供管理接口支持手动刷新

## 后续规划

详细路线图见 **`项目升级规划.md`** / **`项目升级执行计划.md`**。

**当前已完成**：
- ✅ 核心业务功能（用户、餐厅、订单、评价）
- ✅ AI 模块集成（RAG、Function Calling、MCP）
- ✅ 中间件集成（Elasticsearch、RabbitMQ、Redis、Redisson）
- ✅ 热门榜单与延时任务
- ✅ 监控体系（Prometheus）

**待完成**：
- ⏳ 限流熔断（Sentinel）
- ⏳ 分布式事务（Seata）
- ⏳ 文件上传（OSS）
- ⏳ 前端完善（Vue 3 + Element Plus）
