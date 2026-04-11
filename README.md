# 智能美食推荐与餐厅管理系统

基于 Spring Boot 3 + LangChain4j 构建的 AI 驱动餐厅管理平台，集成 RAG 知识库、Function Calling、MCP 联网搜索等 AI 能力，同时涵盖完整的餐厅、订单、评价业务流程。

---

## 技术栈

| 分类 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.5 |
| AI 框架 | LangChain4j 1.1.x |
| 大语言模型 | 通义千问 qwen-plus（DashScope） |
| 嵌入模型 | text-embedding-v4（向量维度 1536） |
| 持久层 | MyBatis-Plus 3.5.x + MySQL 8 |
| 缓存 | Redis + Redisson |
| 消息队列 | RabbitMQ |
| 搜索引擎 | Elasticsearch 8.x + IK 分词器 |
| 认证 | JWT |
| 监控 | Micrometer + Prometheus |
| 工具库 | Hutool |

---

## 核心功能

### AI 能力
- **多轮对话**：基于 Redis 持久化会话记忆，支持跨实例的上下文连续对话
- **RAG 知识库**：美食知识文档向量化存入 Redis，用户提问时检索相关片段增强回答质量
- **Function Calling**：餐厅预订工具、个性化推荐工具（分析用户历史订单和评价偏好）
- **MCP 联网搜索**：接入智谱 BigModel MCP Server，处理实时信息查询
- **AI 评价审核**：异步识别违规评价，返回 APPROVE / REJECT / MANUAL_REVIEW 三种状态
- **AI 餐厅摘要**：自动生成餐厅口碑摘要，结果缓存 24 小时

### 业务功能
- 用户注册 / 登录（BCrypt 加密 + JWT 认证）
- 餐厅管理、菜品管理
- 订单创建、状态流转、超时自动取消
- 评价提交（DFA 敏感词过滤 + AI 语义审核双层）
- 热门榜单（近 30 天订单数统计，Redis ZSet 排序）
- 餐厅全文搜索（ES BoolQuery，支持模糊匹配、分类筛选、评分过滤、关键词高亮）

---

## 快速开始

### 前置依赖

| 依赖 | 版本要求 | 默认端口 |
|------|---------|---------|
| Java | 17+ | - |
| MySQL | 8.0+ | 3306 |
| Redis | 6.0+ | 6379 |
| RabbitMQ | 3.x+ | 5672 |
| Elasticsearch | 8.x（需安装 IK 分词插件） | 9200 |

### 1. 初始化数据库

```bash
mysql -u root -p < sql/all_in_one.sql
```

### 2. 启动中间件

```bash
# RabbitMQ
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management

# Elasticsearch（需提前安装 IK 分词插件）
docker run -d --name elasticsearch -p 9200:9200 \
  -e "discovery.type=single-node" \
  elasticsearch:8.11.0
```

### 3. 配置 API Key

在 `src/main/resources/application-dev.yaml` 中配置：

```yaml
dashscope:
  api-key: your_dashscope_api_key   # 通义千问，必填

bigmodel:
  api-key: your_bigmodel_api_key    # 智谱 MCP 联网搜索，选填
```

### 4. 启动应用

```bash
cd consultant
mvn spring-boot:run
```

首次启动会自动完成：
- 美食知识库向量化写入 Redis（约数十秒，后续启动跳过）
- ES 索引创建并全量导入餐厅数据

### 5. 访问

| 地址 | 说明 |
|------|------|
| http://localhost:8080/index.html | 前端页面（AI 聊天） |
| http://localhost:8080/actuator/prometheus | Prometheus 监控指标 |
| http://localhost:15672 | RabbitMQ 管理界面（admin/admin123） |

---

## 架构设计

### 系统架构

请求从 Controller 层进入，AI 聊天请求由 LangChain4j AiServices 代理处理，代理内部自动完成记忆加载、RAG 检索、工具调用、模型请求的完整流程。业务请求经 Service 层处理后写入 MySQL，耗时操作（AI 审核、ES 同步）通过 RabbitMQ 异步解耦，不阻塞主流程。

```
前端 (Vue3 SPA)
    ↓
Controller 层
    ├── AI 聊天 → LangChain4j AiServices → 通义千问
    │              ├── RedisChatMemoryStore（多轮记忆）
    │              ├── RedisEmbeddingStore（RAG 检索）
    │              ├── FoodReservationTool / RecommendationTool（本地工具）
    │              └── McpToolProvider（智谱联网搜索）
    └── 业务接口 → Service → MySQL
                      ↓
                  RabbitMQ（异步）
                      ├── 评价审核 → AI 分析 → 更新状态
                      ├── 评分更新 → 删缓存 → 同步 ES
                      └── 订单超时 → 自动取消
```

### 数据库设计

核心表：`user`、`restaurant`、`dish`、`order_info`、`review`、`restaurant_reservation`

关键设计：
- `user` 表 `username`、`phone` 字段加唯一索引，防止并发注册重复数据
- `review` 表 `(user_id, order_id)` 加唯一索引，防止重复评价
- 所有表使用逻辑删除（`is_deleted` 字段），MyBatis-Plus 自动过滤

### 缓存设计

| Key 模式 | 内容 | TTL |
|---------|------|-----|
| `chat:memory:{memoryId}` | 会话消息列表（JSON） | 1 天 |
| `restaurant:info:{id}` | 餐厅详情 | 72 小时 + 随机抖动 |
| `user:info:{id}` | 用户信息 | 30 分钟 |
| `restaurant:ai:summary:{id}` | AI 生成的餐厅摘要 | 24 小时 |
| `hot:restaurant:rank` | 热门榜单 ZSet（score=订单数） | 持久 |
| `hot:restaurant:detail:{id}` | 榜单餐厅详情预热缓存 | 40 天 |
| `order:no_show:delay:zset` | 订单超时延时队列（score=超时时间戳） | 持久 |
| `food-knowledge-index` | 美食知识向量索引（RAG） | 300 天 |

缓存防护：封装 `CacheClient`，统一处理穿透（空值哨兵）、击穿（Redisson 互斥锁 + Double Check）、雪崩（随机 TTL）。

### 消息队列设计

| 交换机 | 队列 | 用途 |
|--------|------|------|
| `review.exchange` | `review.audit.queue` | 评价 AI 审核 |
| `review.dlx.exchange` | `review.audit.dlx.queue` | 审核死信（重试失败兜底） |
| `review.rating.exchange` | `review.rating.queue` | 评分更新触发 |
| `restaurant.es.sync.exchange` | `restaurant.es.sync.queue` | 餐厅数据同步到 ES |
| `order.dlx.exchange` | `order.confirm.timeout.queue` | 订单确认超时处理 |

可靠性保障：生产者确认（ConfirmCallback + ReturnsCallback）+ 消费者去重（Redis SETNX）+ 失败重试（最多 3 次）+ 死信队列兜底。

---

## 项目结构

```
consultant/
├── src/main/java/cn/zjw/
│   ├── ai/                  # AI 模块（AiServices、RAG、工具、MCP）
│   ├── common/              # 公共组件（CacheClient、常量、异常、工具类）
│   ├── config/              # 配置类（Redis、Redisson、RabbitMQ、MVC）
│   ├── controller/          # 接口层
│   ├── mq/                  # 消息队列（消费者、事件、去重）
│   ├── search/              # ES 搜索（文档模型、索引服务、初始化）
│   ├── service/             # 业务逻辑
│   ├── task/                # 定时任务（榜单刷新、订单超时扫描）
│   └── mapper/              # 数据访问层
├── src/main/resources/
│   ├── content/             # RAG 知识库文档（.txt）
│   ├── system.txt           # AI 系统提示词
│   └── static/index.html    # 前端页面
└── sql/                     # 数据库初始化脚本
```
