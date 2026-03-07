# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Spring Boot 3.5.9 和 LangChain4j 的 AI 美食顾问聊天机器人应用。使用阿里云通义千问（Qwen）作为大语言模型，结合 RAG（检索增强生成）和 Function Calling 技术，提供智能美食咨询和餐厅预订服务。

**技术栈**: Spring Boot 3.5.9 + LangChain4j 1.0.1-beta6 + 通义千问 + Redis + MySQL + Vue 3

## 构建和运行命令

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName

# 打包应用
mvn clean package

# 运行应用（开发模式）
mvn spring-boot:run

# 运行应用并指定 API Key
API-KEY=your_key mvn spring-boot:run    # Linux/Mac
set API-KEY=your_key && mvn spring-boot:run    # Windows

# 运行打包后的 jar
java -jar target/consultant-0.0.1-SNAPSHOT.jar
```

**访问地址**:
- 前端界面: http://localhost:8080/index.html
- API 接口: http://localhost:8080/chat?memoryId={会话ID}&message={消息内容}

## 运行前置条件

1. **MySQL 数据库** (端口 3307)
   - 数据库名: `volunteer`
   - 用户名/密码: `root/123456` (见 `application.yaml`)
   - 需要创建表: `restaurant_reservation`，建表 SQL：
   ```sql
   CREATE TABLE restaurant_reservation (
       id BIGINT PRIMARY KEY AUTO_INCREMENT,
       name VARCHAR(100) NOT NULL COMMENT '预订人姓名',
       phone VARCHAR(20) NOT NULL COMMENT '联系电话',
       reservation_time DATETIME NOT NULL COMMENT '预订时间',
       restaurant_name VARCHAR(200) NOT NULL COMMENT '餐厅名称',
       people_count INT NOT NULL COMMENT '用餐人数',
       special_request TEXT COMMENT '特殊要求'
   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
   ```

2. **Redis 服务** (端口 6379)
   - 用于聊天记忆存储（ChatMemoryStore）
   - 用于向量数据库（EmbeddingStore）
   - 无需密码配置

3. **阿里云 API 密钥**
   - 需要设置环境变量: `API-KEY`
   - 获取地址: https://dashscope.aliyun.com/
   - 设置方式:
     - Windows: `set API-KEY=your_key_here` 或系统环境变量
     - Linux/Mac: `export API-KEY=your_key_here` 或 `~/.bashrc`
     - IDEA: Run Configuration → Environment variables
   - 用于调用通义千问模型和嵌入模型

## 核心架构

### 数据流图

一个完整的聊天请求流程：
```
用户前端 (index.html)
    ↓ GET /chat?memoryId=xxx&message=xxx
ChatController
    ↓ 调用 consultantService.chat()
ConsultantService (@AiService 接口)
    ├─→ ChatMemoryProvider (从 Redis 加载历史对话)
    ├─→ ContentRetriever (从 Redis 向量库检索相关文档)
    ├─→ 组装 Prompt 发送到通义千问
    ↓
通义千问 API 响应
    ├─→ 如需预订餐厅: 调用 FoodReservationTool
    │        ↓ 写入 MySQL restaurant_reservation 表
    ├─→ 流式返回 AI 回复 (Flux<String>)
    ↓
前端展示打字机效果
```

### 1. AI 服务层 (`ConsultantService.java`)

这是一个使用 `@AiService` 注解的接口，配置了完整的 AI 能力：

```java
@AiService(
    wiringMode = AiServiceWiringMode.EXPLICIT,
    chatModel = "openAiChatModel",
    streamingChatModel = "openAiStreamingChatModel",
    chatMemoryProvider = "chatMemoryProvider",
    contentRetriever = "contentRetriever",
    tools = "foodReservationTool"
)
```

- **流式响应**: 使用 `Flux<String>` 返回类型实现打字机效果
- **多会话管理**: 通过 `@MemoryId` 参数隔离不同用户的聊天历史
- **系统提示词**: 从 `resources/system.txt` 加载

### 2. RAG 架构 (`CommonConfig.java`)

**工作流程**:
1. 从 `resources/content/` 目录加载美食相关文档（txt 格式）
2. 使用递归分割器切分文档（500 字符块，100 字符重叠）
3. 使用 `text-embedding-v3` 模型生成嵌入向量
4. 存储到 Redis 向量数据库
5. 查询时检索最相关的 3 个文档片段（相似度阈值 0.5）

**关键配置**:
```java
DocumentSplitter ds = DocumentSplitters.recursive(500, 100);
ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(redisEmbeddingStore)
    .minScore(0.5)
    .maxResults(3)
    .embeddingModel(embeddingModel)
    .build();
```

### 3. 多会话记忆管理 (`RedisChatMemoryStore.java`)

- 每个会话通过 `memoryId` 隔离
- 保留最近 20 条消息
- 使用 Redis 持久化存储
- 数据自动过期时间: 1 天

### 4. Function Calling (`FoodReservationTool.java`)

AI 可自动调用的工具方法：
- `reserveRestaurant()` - 预订餐厅
- `findReservation()` - 查询预订信息

这些工具连接到 MySQL 数据库的 `restaurant_reservation` 表。

### 5. 前端架构 (`static/index.html`)

- Vue 3 Composition API
- 流式响应处理（Server-Sent Events）
- 打字机效果动画
- 暗黑模式支持
- 每次新建会话生成新的 `memoryId`

## 配置说明

### AI 模型配置 (`application.yaml`)

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
    embedding-model:
      model-name: text-embedding-v3
```

使用阿里云通义千问的 OpenAI 兼容接口。

### 数据库配置

- **MySQL**: `jdbc:mysql://localhost:3307/volunteer`
- **Redis**: `localhost:6379`
- MyBatis 配置了驼峰命名转换

## 项目结构

```
src/main/java/cn/zjw/
├── ConsultantApplication.java          # Spring Boot 启动类
├── aiservice/
│   └── ConsultantService.java          # AI 服务接口（核心）
├── Config/
│   └── CommonConfig.java               # RAG、记忆、向量库配置
├── controller/
│   └── ChatController.java             # 聊天 API 控制器
├── mapper/
│   └── FoodReservationMapper.java      # MyBatis 数据访问层
├── pojo/
│   └── FoodReservation.java            # 餐厅预订实体类
├── repository/
│   └── RedisChatMemoryStore.java       # Redis 聊天记忆实现
├── service/
│   └── FoodReservationService.java     # 餐厅预订业务逻辑
└── tools/
    └── FoodReservationTool.java        # AI 工具（Function Calling）

src/main/resources/
├── application.yaml                    # 应用配置
├── system.txt                          # AI 系统提示词
├── content/                            # RAG 知识库文档
│   ├── 美食指南.txt
│   ├── 健康饮食指南.txt
│   ├── 成都小众美食攻略.txt
│   ├── 家庭秘制红烧肉.txt
│   └── 粤菜经典菜品制作.txt
└── static/
    └── index.html                      # Vue 单页应用前端
```

## 开发注意事项

### 首次启动必看

1. **向量库初始化**: 首次启动时 `CommonConfig.store()` 会自动加载 `resources/content/` 下的所有文档并向量化，这个过程可能需要 30-60 秒。后续启动会复用 Redis 中的向量数据。
2. **清空向量库重建**: 如果需要完全重建向量库，需手动清空 Redis 中的相关键或重启 Redis。

### 修改 RAG 知识库

1. 在 `src/main/resources/content/` 目录添加或修改 txt 文件
2. 重启应用，`CommonConfig.store()` 方法会自动重新加载和向量化文档
3. 文档会被切分成 500 字符的块，重叠 100 字符

### 修改系统提示词

编辑 `src/main/resources/system.txt` 文件，重启应用生效。

### 添加新的 AI 工具

1. 在 `tools/` 包下创建新的工具类
2. 使用 `@Tool` 注解标记方法
3. 在 `ConsultantService` 的 `@AiService` 注解中添加工具 Bean 名称

### 调整记忆窗口大小

在 `CommonConfig.chatMemoryProvider()` 方法中修改 `.maxMessages(20)` 参数。

### 调整 RAG 检索参数

在 `CommonConfig.contentRetriever()` 方法中修改：
- `.minScore(0.5)` - 相似度阈值
- `.maxResults(3)` - 返回结果数量

## 日志调试

LangChain4j 的日志级别设置为 `debug`，会输出：
- API 请求和响应内容
- 向量检索结果
- Function Calling 调用详情

查看日志可以帮助理解 AI 的决策过程。

### 常见调试场景

1. **AI 没有调用工具**: 检查日志中是否有 `Tool execution` 相关输出，可能需要调整系统提示词让 AI 更倾向于使用工具
2. **RAG 检索不到相关内容**:
   - 检查日志中 `Retrieved segments` 输出的相似度分数
   - 尝试降低 `minScore` 阈值或增加 `maxResults` 数量
3. **记忆窗口丢失**: 检查 Redis 连接状态，确认 `memoryId` 是否一致
4. **API 调用超时**: 通义千问接口可能有限流，检查日志中的错误信息

## 性能优化建议

- **向量检索性能**: Redis 向量搜索对于中小规模数据集（<10万条）性能良好，超大规模建议迁移到专业向量数据库如 Milvus
- **流式响应延迟**: `qwen-plus` 模型首 token 延迟约 500-1000ms，可考虑使用 `qwen-turbo` 降低延迟
- **记忆管理**: 当前保留 20 条消息，若对话很长可能导致 token 超限，可通过 `MessageWindowChatMemory` 的 `maxMessages` 参数调整
