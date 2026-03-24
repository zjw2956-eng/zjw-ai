# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 **Spring Boot 3.5.x** 和 **LangChain4j** 的 **智能美食推荐与餐厅管理系统**。使用阿里云 **通义千问（DashScope）** 作为大语言模型与嵌入模型，结合 **RAG**、本地 **Function Calling**（`@Tool`）以及 **智谱 MCP 联网搜索**（可选），提供智能美食咨询、餐厅预订、用户管理、订单管理等能力。

**技术栈**: Spring Boot 3.5.0 + LangChain4j 1.1.x + MyBatis-Plus 3.5.x + 通义千问（DashScope Starter）+ Redis + MySQL + Hutool；前端为 `static/index.html`（Vue 3 单页）。

**项目结构**: 应用代码位于 `consultant/` 子目录下；数据库初始化脚本位于 `consultant/sql/`（若仓库另有顶层 `sql/`，以实际路径为准）。

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
- 餐厅分页: `GET /api/restaurant/page`

## 运行前置条件

1. **Java 17**

2. **MySQL**（默认 profile `work` 下为端口 **3307**）
   - 数据库名: `food_ai_system`
   - 用户名/密码见 `application.yaml` 与各 profile
   - 执行 `consultant/sql/` 下脚本初始化（如 `all_in_one.sql`）

3. **Redis**（默认 **6380**）
   - 聊天记忆：`chat:memory:{memoryId}`
   - 向量索引：`food-knowledge-index`（RAG）
   - 知识库一次性初始化标记：`knowledge:initialized`
   - JWT：`user:token:{userId}`（见 `Constants`）

4. **API 密钥**
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
│  - ReviewController / DishController                         │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ AI 组装层                                                    │
│  - AiServiceFactory：AiServices.builder 注册 AiHelperService │
│  - RagConfig / KnowledgeBaseInitializer                      │
│  - McpConfig（McpToolProvider → 智谱 web_search）             │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ Service / Mapper / 数据访问                                  │
│  - UserService、RestaurantService、OrderService 等            │
│  - 无单独的 FoodReservationService；预订由 AI Tool 写库       │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│ 数据存储                                                     │
│  - MySQL：业务数据                                           │
│  - Redis：记忆 + 向量 + Token + 知识库初始化标记             │
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

### 4. 本地工具：`FoodReservationTool`

- 包路径：**`cn.zjw.ai.tools`**。
- 表名：**`restaurant_reservation`**（实体 `Reservation`）。
- 方法：`reserveRestaurant`、`findReservation`（`@Tool`）。

### 5. MCP 联网搜索：`McpConfig`

- **`McpToolProvider`** + **`DefaultMcpClient`** + **`HttpMcpTransport`**（智谱 **`web_search` SSE** URL，需 **`bigmodel.api-key`**）。
- 工具由远端 MCP 下发（如 `webSearchPro`、`webSearchSogou` 等）；是否调用由**模型决定**，并非每条用户消息都会执行搜索。
- SSE 长连接在空闲时可能出现 **读超时与自动重连**（日志中可见），属传输层常见现象。

### 6. `ChatController`

- 映射：**`/ai/chat`**（类上 **`@RequestMapping("/ai")`**）。
- 返回 **`CommonResult<String>`**，**非流式**（非 `Flux` / SSE）。

## 配置说明

### DashScope（`application.yaml`）

```yaml
langchain4j:
  community:
    dashscope:
      chat-model:
        api-key: ${dashscope.api-key}
        model-name: qwen-plus
      embedding-model:
        model-name: text-embedding-v4
```

- Profile（如 `application-work.yaml`）中设置 **`dashscope.api-key`**。
- **`spring.profiles.active`** 默认为 **`work`**（可按环境修改）。

### 智谱 MCP（`application.yaml`）

```yaml
bigmodel:
  api-key: ${bigmodel.api-key}
```

- 各环境在 profile 中设置 **`bigmodel.api-key`**。

### 数据库与 Redis

- MySQL URL 在各 profile 的 `spring.datasource.url` 中。
- Redis：`spring.data.redis.host` / `port`（默认 `localhost:6380`）。

### JWT

- **`jwt.secret`**、**`jwt.expire-hours`**（如 720）、**`jwt.header`**：`Authorization: Bearer <token>`。
- 登录拦截仅拦截 **`/api/**`**（见 `WebMvcConfig`）；**`/ai/chat` 不要求 JWT**。

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
│   │   └── tools/FoodReservationTool.java
│   ├── common/ …
│   ├── config/       # WebMvcConfig、MybatisPlusConfig、RedisConfig 等
│   ├── controller/   # User、Restaurant、Order、Review、Dish
│   ├── service/、mapper/、pojo/、interceptor/、handler/
│   └── …
├── src/main/resources/
│   ├── application.yaml
│   ├── application-work.yaml / application-home.yaml
│   ├── system.txt
│   ├── content/      # RAG 知识库 txt
│   └── static/index.html
├── sql/              # 数据库脚本
└── pom.xml
```

## 开发注意事项

### 首次启动

1. 执行 SQL 初始化（MySQL）。
2. 配置 **`dashscope.api-key`**；若使用 MCP，配置 **`bigmodel.api-key`**。
3. 首次启动会进行知识库向量化（若 Redis 无 `knowledge:initialized`），需数十秒级时间；后续启动会跳过。
4. 若需**强制重建向量库**：删除 Redis 中 `knowledge:initialized` 及向量相关数据（或按运维要求清空对应索引）。

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

## 日志与调试

- 可将 `logging.level.dev.langchain4j` 等设为 `DEBUG` 查看模型与 RAG 行为（按需在 `application.yaml` 配置）。
- **MCP**：`McpConfig` 中可开关 **`logRequests` / `logResponses`**；SSE 超时重连会打 WARN，需与业务错误区分。

## 与旧文档的主要差异（备忘）

- 已不再使用 **`ConsultantService` + 接口 `@AiService` 全自动装配**；当前为 **`AiHelperService` + `AiServiceFactory`**。
- RAG 与向量库不在 **`CommonConfig`**，而在 **`RagConfig` + `KnowledgeBaseInitializer`**。
- 聊天路径为 **`/ai/chat`**，返回**非流式** `CommonResult`。
- 增加 **智谱 MCP** 联网搜索；密钥与 DashScope **分开配置**。

## 后续规划

详细路线图见 **`项目升级规划.md`** / **`项目升级执行计划.md`**。
