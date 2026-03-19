# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个基于 Spring Boot 3.5.9 和 LangChain4j 的 **智能美食推荐与餐厅管理系统**。使用阿里云通义千问（Qwen）作为大语言模型，结合 RAG（检索增强生成）和 Function Calling 技术，提供智能美食咨询、餐厅预订、用户管理、订单管理等完整功能。

**技术栈**: Spring Boot 3.5.9 + LangChain4j 1.0.1-beta6 + MyBatis-Plus 3.5.x + 通义千问 + Redis + MySQL + Vue 3 + Hutool

**项目结构**: 项目代码位于 `consultant/` 子目录下

## 构建和运行命令

**注意**: 所有命令需要在 `consultant/` 目录下执行

```bash
# 进入项目目录
cd consultant

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
- AI聊天接口: http://localhost:8080/chat?memoryId={会话ID}&message={消息内容}
- 用户注册: POST http://localhost:8080/api/user/register
- 用户登录: POST http://localhost:8080/api/user/login
- 餐厅查询: GET http://localhost:8080/api/restaurant/page

## 运行前置条件

1. **Java 17** - 项目使用 Java 17，确保已安装并配置 `JAVA_HOME` 环境变量

2. **MySQL 数据库** (端口 3307)
   - 数据库名: `food_ai_system`
   - 用户名/密码: `root/123456` (见 `application.yaml`)
   - 需要执行数据库初始化脚本（位于项目根目录 `sql/` 文件夹）：
   ```bash
   # 方式1：一键执行（推荐）
   mysql -uroot -p123456 -P3307 < sql/all_in_one.sql

   # 方式2：分步执行
   mysql -uroot -p123456 -P3307 < sql/01_create_database_and_core_tables.sql
   mysql -uroot -p123456 -P3307 < sql/02_create_other_tables.sql
   mysql -uroot -p123456 -P3307 < sql/03_insert_test_data_users_restaurants.sql
   mysql -uroot -p123456 -P3307 < sql/04_insert_test_data_dishes.sql
   mysql -uroot -p123456 -P3307 < sql/05_insert_test_data_orders_reviews.sql
   ```

   **数据库表结构**:
   - `user` - 用户表
   - `restaurant` - 餐厅表
   - `dish` - 菜品表
   - `order_info` - 订单表
   - `review` - 评价表
   - `user_tag` - 用户标签表
   - `restaurant_reservation` - 餐厅预订表（AI Function Calling使用）

3. **Redis 服务** (端口 6380)
   - 用于聊天记忆存储（ChatMemoryStore）
   - 用于向量数据库（EmbeddingStore）
   - 用于用户登录态存储（JWT Token，key格式：`user:token:{userId}`）
   - 无需密码配置

4. **阿里云 API 密钥**
   - 需要设置环境变量: `API-KEY`
   - 获取地址: https://dashscope.aliyun.com/
   - 设置方式:
     - Windows: `set API-KEY=your_key_here` 或系统环境变量
     - Linux/Mac: `export API-KEY=your_key_here` 或 `~/.bashrc`
     - IDEA: Run Configuration → Environment variables
   - 用于调用通义千问模型和嵌入模型

## 核心架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        前端层 (Vue 3)                        │
│  - index.html (AI聊天界面)                                   │
│  - 用户注册/登录界面 (待开发)                                │
│  - 餐厅浏览/预订界面 (待开发)                                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Controller 层 (控制器)                    │
│  - ChatController (AI聊天)                                   │
│  - UserController (用户注册/登录/信息管理)                   │
│  - RestaurantController (餐厅查询/详情)                      │
│  - OrderController (订单管理)                                │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                     Service 层 (业务逻辑)                    │
│  - ConsultantService (@AiService - AI服务)                  │
│  - UserService (用户业务)                                    │
│  - RestaurantService (餐厅业务)                              │
│  - OrderService (订单业务)                                   │
│  - FoodReservationService (预订业务)                         │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Mapper 层 (数据访问)                      │
│  - UserMapper (MyBatis-Plus)                                 │
│  - RestaurantMapper                                          │
│  - OrderMapper                                               │
│  - ReviewMapper                                              │
│  - FoodReservationMapper                                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      数据存储层                              │
│  - MySQL (food_ai_system) - 业务数据                        │
│  - Redis - 聊天记忆 + 向量数据库 + 用户Token                │
└─────────────────────────────────────────────────────────────┘
```

### AI聊天数据流图

一个完整的AI聊天请求流程：
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

- **MySQL**: `jdbc:mysql://localhost:3307/food_ai_system`
- **Redis**: `localhost:6380`
- MyBatis-Plus 配置：
  - 驼峰命名自动转换
  - 逻辑删除支持（字段：isDeleted，删除值：1，未删除值：0）
  - SQL 日志输出到控制台

### JWT 配置

- **密钥**: 配置在 `application.yaml` 的 `jwt.secret`
- **过期时间**: 72 小时
- **Token 格式**: `Authorization: Bearer <token>`
- **存储方式**: Token 同时存储在 Redis 中（key: `user:token:{userId}`），用于校验和单点登录控制

## 项目结构

```
D:\aizjw\aiwork\                        # 项目根目录
├── consultant/                         # Spring Boot 应用目录
│   ├── src/main/java/cn/zjw/
│   │   ├── ConsultantApplication.java          # Spring Boot 启动类
│   │   ├── aiservice/
│   │   │   └── ConsultantService.java          # AI 服务接口（核心）
│   │   ├── common/                             # 公共模块
│   │   │   ├── constant/Constants.java         # 常量类
│   │   │   ├── context/UserContext.java        # 用户上下文（ThreadLocal）
│   │   │   ├── enums/                          # 枚举（OrderStatus、DishStatus、ReviewStatus）
│   │   │   ├── exception/                      # 业务异常、未授权异常
│   │   │   ├── model/PageParams.java           # 分页参数
│   │   │   ├── result/CommonResult.java        # 统一返回结果
│   │   │   ├── result/ResultCode.java          # 返回码枚举
│   │   │   ├── result/PageResult.java          # 分页返回结果
│   │   │   └── utils/JwtUtil.java              # JWT工具类
│   │   ├── config/
│   │   │   ├── AiConfig.java                   # AI配置（RAG、记忆、向量库）
│   │   │   ├── MybatisPlusConfig.java          # MyBatis-Plus配置
│   │   │   ├── RedisConfig.java                # Redis配置
│   │   │   └── WebMvcConfig.java               # Web MVC配置
│   │   ├── controller/                         # 控制层
│   │   │   ├── ChatController.java             # AI聊天控制器
│   │   │   ├── UserController.java             # 用户控制器
│   │   │   ├── RestaurantController.java       # 餐厅控制器
│   │   │   ├── OrderController.java            # 订单控制器
│   │   │   ├── ReviewController.java           # 评价控制器
│   │   │   └── DishController.java             # 菜品控制器
│   │   ├── service/                            # 业务层
│   │   │   ├── UserService.java
│   │   │   ├── RestaurantService.java
│   │   │   ├── OrderService.java
│   │   │   ├── ReviewService.java
│   │   │   ├── DishService.java
│   │   │   ├── FoodReservationService.java
│   │   │   └── impl/
│   │   │       ├── UserServiceImpl.java
│   │   │       ├── RestaurantServiceImpl.java
│   │   │       ├── OrderServiceImpl.java
│   │   │       ├── ReviewServiceImpl.java
│   │   │       └── DishServiceImpl.java
│   │   ├── mapper/                             # 数据访问层
│   │   │   ├── UserMapper.java
│   │   │   ├── RestaurantMapper.java
│   │   │   ├── OrderMapper.java
│   │   │   ├── ReviewMapper.java
│   │   │   ├── DishMapper.java
│   │   │   └── FoodReservationMapper.java
│   │   ├── pojo/                               # 实体类
│   │   │   ├── entity/                         # 数据库实体
│   │   │   │   ├── User.java                   # 用户实体
│   │   │   │   ├── Restaurant.java             # 餐厅实体
│   │   │   │   ├── Dish.java                   # 菜品实体
│   │   │   │   ├── OrderInfo.java              # 订单实体
│   │   │   │   ├── Review.java                 # 评价实体
│   │   │   │   ├── UserTag.java                # 用户标签实体
│   │   │   │   └── FoodReservation.java        # 餐厅预订实体
│   │   │   ├── dto/                            # 数据传输对象
│   │   │   │   ├── UserRegisterDTO.java        # 用户注册DTO
│   │   │   │   ├── UserLoginDTO.java           # 用户登录DTO
│   │   │   │   └── RestaurantQueryDTO.java     # 餐厅查询DTO
│   │   │   └── vo/                             # 视图对象
│   │   │       ├── UserVO.java                 # 用户信息VO
│   │   │       └── RestaurantVO.java           # 餐厅信息VO
│   │   ├── interceptor/                        # 拦截器
│   │   │   └── AuthInterceptor.java            # 登录校验拦截器
│   │   ├── handler/                            # 处理器
│   │   │   ├── GlobalExceptionHandler.java     # 全局异常处理器
│   │   │   └── MyMetaObjectHandler.java        # MyBatis-Plus自动填充
│   │   ├── repository/
│   │   │   └── RedisChatMemoryStore.java       # Redis 聊天记忆实现
│   │   └── tools/
│   │       └── FoodReservationTool.java        # AI 工具（Function Calling）
│   ├── src/main/resources/
│   │   ├── application.yaml                    # 应用配置
│   │   ├── system.txt                          # AI 系统提示词
│   │   ├── content/                            # RAG 知识库文档
│   │   │   ├── 美食指南.txt
│   │   │   ├── 健康饮食指南.txt
│   │   │   ├── 成都小众美食攻略.txt
│   │   │   ├── 家庭秘制红烧肉.txt
│   │   │   └── 粤菜经典菜品制作.txt
│   │   └── static/
│   │       └── index.html                      # Vue 单页应用前端
│   └── pom.xml                                 # Maven 依赖配置
├── sql/                                        # 数据库初始化脚本
│   ├── all_in_one.sql                          # 一键执行脚本
│   ├── 01_create_database_and_core_tables.sql  # 创建数据库和核心表
│   ├── 02_create_other_tables.sql              # 创建其他表
│   ├── 03_insert_test_data_users_restaurants.sql # 插入用户和餐厅测试数据
│   ├── 04_insert_test_data_dishes.sql          # 插入菜品测试数据
│   └── 05_insert_test_data_orders_reviews.sql  # 插入订单和评价测试数据
├── CLAUDE.md                                   # 项目技术文档（本文件）
├── MVC架构说明.md                              # MVC架构搭建说明
└── 项目升级规划.md                             # 项目升级规划路线图
```

## 开发注意事项

### 项目目录说明
- **所有代码开发工作在 `consultant/` 目录下进行**
- **所有 Maven 命令需要在 `consultant/` 目录下执行**
- SQL 脚本位于项目根目录的 `sql/` 文件夹

### 首次启动必看

1. **数据库初始化**: 首次启动前必须执行 SQL 初始化脚本
   ```bash
   mysql -uroot -p123456 -P3307 < sql/all_in_one.sql
   ```

2. **向量库初始化**: 首次启动时 `CommonConfig.store()` 会自动加载 `resources/content/` 下的所有文档并向量化，这个过程可能需要 30-60 秒。后续启动会复用 Redis 中的向量数据。

3. **清空向量库重建**: 如果需要完全重建向量库，需手动清空 Redis 中的相关键或重启 Redis。

### 当前开发状态

**✅ 已完成**:
- MVC 架构框架搭建
- 数据库表结构设计（7张表）
- AI 聊天功能（RAG + Function Calling）
- 统一返回结果封装（`CommonResult`）
- 全局异常处理
- MyBatis-Plus 集成
- JWT 工具类 + 登录拦截器（Token 校验 + Redis 单点登录）
- 用户注册/登录（BCrypt 加密 + JWT + Redis）
- 餐厅分页查询、餐厅详情（含 Redis 缓存）
- 订单创建、查询、取消
- 评价发表、审核、查询、删除
- 菜品相关接口（DishController/DishService）

**🚧 待实现**:
- 餐厅新增/修改接口（`addRestaurant`、`updateRestaurant` 有 TODO）
- 缓存一致性：菜品变更时删除餐厅缓存
- 订单幂等性校验
- 前端用户界面（目前只有 AI 聊天界面）

### 修改 RAG 知识库

1. 在 `consultant/src/main/resources/content/` 目录添加或修改 txt 文件
2. 重启应用，`CommonConfig.store()` 方法会自动重新加载和向量化文档
3. 文档会被切分成 500 字符的块，重叠 100 字符

### 修改系统提示词

编辑 `consultant/src/main/resources/system.txt` 文件，重启应用生效。

### 添加新的 AI 工具

1. 在 `consultant/src/main/java/cn/zjw/tools/` 包下创建新的工具类
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

## 下一步开发计划

参考 `项目升级规划.md` 文档，按优先级实现以下功能：

### 第一阶段：完善基础功能
1. ✅ 实现用户注册/登录逻辑（JWT + Redis）
2. ✅ 实现登录拦截器的 Token 校验
3. ✅ 实现餐厅查询/详情接口
4. ✅ 实现订单管理接口

### 第二阶段：增强功能
1. 添加用户标签系统（口味偏好）
2. 实现评价系统
3. 添加餐厅收藏功能
4. 实现个性化推荐

### 第三阶段：技术升级
1. 集成消息队列（RabbitMQ/RocketMQ）
2. 集成搜索引擎（Elasticsearch）
3. 添加限流熔断（Sentinel）
4. 添加分布式定时任务（XXL-Job）

详细规划请查看 `项目升级规划.md`。
