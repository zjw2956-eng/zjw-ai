# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

基于 **Spring Boot 3.5.0** + **LangChain4j 1.1.x** 的智能美食推荐与餐厅管理系统。使用阿里云通义千问（DashScope）作为 LLM 与嵌入模型，结合 RAG、Function Calling（`@Tool`）、智谱 MCP 联网搜索，提供 AI 美食咨询、餐厅预订、订单管理等能力。

**技术栈**: Spring Boot 3.5.0 + LangChain4j 1.1.x + MyBatis-Plus 3.5.x + MySQL + Redis + Redisson + RabbitMQ + Elasticsearch + 阿里云 OSS + Hutool + Prometheus

## 构建与运行

所有命令在 `consultant/` 目录下执行：

```bash
mvn clean compile
mvn clean package
mvn spring-boot:run
mvn test -Dtest=ClassName#methodName
```

默认激活 profile：`dev`（见 `application.yaml`）。

## 运行前置条件

| 服务 | 默认地址 | 说明 |
|------|---------|------|
| MySQL | localhost:3306 | 数据库 `food_ai_system`，执行 `sql/all_in_one.sql` 初始化 |
| Redis | localhost:6379 | 聊天记忆、向量索引、缓存、延时队列等 |
| RabbitMQ | localhost:5672 | 用户名/密码：admin/admin123 |
| Elasticsearch | localhost:9200 | 餐厅全文搜索，启动时自动初始化索引 |

**必须配置的密钥**（在激活的 profile yaml 中设置）：
- `dashscope.api-key`：通义千问 LLM + 嵌入模型
- `bigmodel.api-key`：智谱 MCP 联网搜索（可选）
- `aliyun.oss.*`：OSS 文件上传（endpoint、accessKeyId、accessKeySecret、bucketName、urlPrefix）

> **安全警告**：`application-dev.yaml` 中不应提交真实密钥到 git。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/ai/chat?memoryId=&message=` | AI 聊天（无需 JWT） |
| POST | `/api/user/register` | 用户注册 |
| POST | `/api/user/login` | 用户登录 |
| GET | `/api/restaurant/page` | 餐厅分页查询 |
| GET | `/api/restaurant/search` | ES 全文搜索 |
| GET | `/api/hot-rank/restaurants?limit=10` | 热门榜单 |
| POST | `/api/review` | 提交评价 |
| POST | `/api/order` | 创建订单 |
| POST | `/api/upload` | 图片上传（OSS，仅支持图片格式，限 10MB） |
| POST | `/api/admin/es/restaurant/rebuild` | 重建 ES 索引（管理员） |
| GET | `/actuator/prometheus` | Prometheus 监控指标 |

JWT 拦截范围：`/api/**`；`/ai/chat` 不需要认证。

## 核心架构

### AI 模块（`cn.zjw.ai`）

- **`AiHelperService`**：接口声明，`@SystemMessage(fromResource = "system.txt")`，方法签名 `String chat(@MemoryId int memoryId, @UserMessage String message)`
- **`AiServiceFactory`**：手动用 `AiServices.builder()` 装配，同时挂载本地工具和 McpToolProvider
- **RAG**：`RedisEmbeddingStore`（索引 `food-knowledge-index`，维度 1536），`maxResults(5)`，`minScore(0.75)`；知识库文件在 `resources/content/*.txt`；用 `knowledge:initialized` 标记是否已向量化
- **会话记忆**：`RedisChatMemoryStore`，key `chat:memory:{memoryId}`，TTL 1天，`maxMessages(10)`
- **本地工具**：`FoodReservationTool`（预订，表 `restaurant_reservation`）、`RecommendationTool`（个性化推荐，查询订单历史/评价/高分餐厅）
- **MCP**：`McpConfig` 连接智谱 SSE，工具由模型按需调用

### 缓存（`cn.zjw.common.cache.CacheClient`）

封装了三种防护策略：
- **穿透**：空值缓存，TTL 2分钟（`Constants.REDIS_EMPTY_KEY_EXPIRE_TIME`）
- **击穿**：`setIfAbsent` 互斥锁 + Redisson 锁（`Constants.LOCK_TTL_SECONDS = 10s`）
- **逻辑过期**：`CacheData<T>` 包装数据和过期时间，异步重建（线程池大小 10）

所有 Redis key 常量集中在 `cn.zjw.common.constant.Constants`。

### RabbitMQ 交换机与队列

| 交换机 | 队列 | 路由键 | 用途 |
|--------|------|--------|------|
| `review.exchange` | `review.audit.queue` | `review.audit` | 评价 AI 审核 |
| `review.dlx.exchange` | `review.audit.dlx.queue` | `review.audit.dlx` | 审核死信 |
| `review.rating.exchange` | `review.rating.queue` | `review.rating` | 评分更新 |
| `order.exchange` | — | — | 订单事件 |
| `order.dlx.exchange` | `order.confirm.timeout.queue` | `order.confirm.timeout` | 订单超时 |
| `hot.rank.refresh.exchange` | `hot.rank.refresh.queue` | `hot.rank.refresh` | 热榜异步刷新 |
| `restaurant.es.sync.exchange` | `restaurant.es.sync.queue` | `restaurant.es.sync` | 餐厅数据同步 ES |

消息可靠性：`ConfirmCallback`（交换机确认）+ `ReturnsCallback`（路由失败）+ Redis 重试元数据（`mq:msgId:{msgId}`，最多 3 次）+ 死信队列兜底。

### 定时任务（`cn.zjw.task`）

- `HotRankScheduler`：每日凌晨 2 点统计近 30 天订单，写入 Redis ZSet `hot:restaurant:rank`
- `OrderNoShowCancelTask`：每 5 秒扫描 `order:no_show:delay:zset`，超时订单自动取消（用餐后 30 分钟）

### OSS 文件上传

`OssUtil` 每次上传创建新 OSS 客户端，上传后必须 `shutdown()`（已在 finally 块中处理）。文件名格式：`images/{date}/{UUID}.{ext}`。配置前缀 `aliyun.oss.*`，由 `OssProperties` 绑定。

## 开发注意事项

**强制重建知识库**：删除 Redis 键 `knowledge:initialized`，重启应用。

**重建 ES 索引**：`POST /api/admin/es/restaurant/rebuild`。

**添加 AI 工具**：在 `cn.zjw.ai.tools` 下新增 `@Component` + `@Tool` 方法，然后在 `AiServiceFactory` 中 `.tools(yourBean)` 注册。

**修改系统提示词**：编辑 `src/main/resources/system.txt`，重启生效。

**调整 RAG 参数**：在 `RagConfig.contentRetriever()` 中修改 `maxResults`、`minScore`。

**调整记忆窗口**：在 `AiServiceFactory` 的 `MessageWindowChatMemory.builder().maxMessages(10)` 中修改。

## 待完成功能

- ⏳ 限流熔断（Sentinel）
- ⏳ 分布式事务（Seata）
- ⏳ 前端完善（Vue 3 + Element Plus）
