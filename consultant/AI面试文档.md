# AI 面试文档（结合项目实战）

> 项目：智能美食推荐与餐厅管理系统  
> 目标：用口语化的方式回答面试问题，结合项目实际场景，体现工程思维。

---

# 一、项目 AI 能力全景

## 面试问：你项目里用了哪些 AI 技术？

项目基于 LangChain4j 1.1.x + 通义千问（DashScope）构建，集成了以下 AI 能力：

| 能力 | 技术方案 | 项目场景 |
|------|---------|---------|
| 多轮对话 | LangChain4j AiServices + MessageWindowChatMemory | AI 美食助手聊天 |
| RAG 知识库检索 | Redis 向量库 + EmbeddingStoreContentRetriever | 美食知识问答增强 |
| Function Calling（本地工具） | @Tool 注解 | 餐厅预订、个性化推荐 |
| MCP 联网搜索 | 智谱 BigModel MCP SSE | 实时网络信息查询 |
| 结构化输出 | @SystemMessage + 返回值类型推断 | 评价审核、餐厅摘要 |
| AI 评价审核 | RabbitMQ 异步 + AI 分析 | 自动识别违规评价 |
| AI 餐厅摘要 | Redis 缓存 + AI 生成 | 餐厅口碑摘要展示 |

---

# 二、LangChain4j 核心概念

## 面试问：LangChain4j 是什么，你怎么用的？

LangChain4j 是 Java 生态的 AI 应用开发框架，类似 Python 的 LangChain，核心思想是把 LLM 调用、记忆、工具、RAG 等能力组合成一个完整的 AI 服务。

最核心的用法是 AiServices，类似 MyBatis 的 @Mapper：只需声明接口，框架在运行时自动生成代理实现类。接口上用 @SystemMessage 指定系统提示词，方法参数用 @MemoryId 标记会话 ID、@UserMessage 标记用户输入，框架自动处理记忆加载、RAG 检索、工具调用、模型请求的完整流程。

项目里在 AiServiceFactory 里用 AiServices.builder(AiHelperService.class) 手动装配，链式注册 chatModel（通义千问）、streamingChatModel（流式）、contentRetriever（RAG 检索器）、tools（本地工具 FoodReservationTool 和 RecommendationTool）、toolProvider（MCP 工具）、chatMemoryProvider（按 memoryId 创建 Redis 记忆），最后 build() 生成代理实现类注入 Spring 容器。

**为什么用手动装配而不是 @AiService 注解？**

@AiService 注解方式不支持同时挂载本地 @Tool 和 McpToolProvider，手动用 AiServices.builder() 可以灵活组合所有能力。

---

# 三、RAG（检索增强生成）

## 面试问：什么是 RAG，你项目里怎么实现的？

RAG（Retrieval-Augmented Generation）的核心思想：模型的训练数据有截止日期，也不包含私有业务知识。RAG 在用户提问时，先从知识库里检索相关内容，把检索结果拼入 Prompt，让模型基于这些内容回答，而不是凭空生成。

解决的问题：模型不知道你的业务知识（菜系介绍、餐厅特色等）；模型可能"幻觉"，RAG 让回答有据可查；不需要微调模型，成本低。

## 项目 RAG 实现流程

### 离线阶段（知识库初始化）

KnowledgeBaseInitializer 在应用启动时执行（@PostConstruct）：

1. 检查 Redis 是否有 knowledge:initialized 标记，有则跳过（避免重复向量化）
2. 加载 classpath:content/*.txt 下所有美食知识文档
3. 用 DocumentSplitters.recursive(500, 100) 切分：最长500字符一块，相邻块有100字符重叠
4. 调用通义千问嵌入模型（text-embedding-v4）批量向量化
5. 存入 Redis 向量库（索引名 food-knowledge-index，维度1536）
6. 写入初始化标记，300天过期

**为什么切分时要有100字符重叠？**

防止关键信息恰好被切断在两个块的边界处，重叠保证上下文连续性。

### 在线阶段（用户提问时）

用户问题先向量化，再在 Redis 向量库里做相似度检索，取 Top5 相关片段，拼入 Prompt，模型基于这些内容回答。

检索器配置：EmbeddingStoreContentRetriever，maxResults(5) 最多取5个相关片段，minScore(0.75) 过滤相似度低于0.75的内容，避免把无关信息塞进 Prompt 干扰模型。

---

# 四、Function Calling（工具调用）

## 面试问：什么是 Function Calling，你项目里怎么用的？

Function Calling 让模型在回答时可以调用外部函数获取实时数据，而不是只靠训练数据回答。

工作流程：用户提问 → 模型判断是否需要调用工具 → 需要则生成工具调用请求（函数名+参数）→ 框架执行本地方法 → 把结果返回给模型 → 模型生成最终回答。不需要时直接回答。

关键点：工具是否调用由模型自己决定，不是每次都调用。

## 项目中的两个本地工具

### FoodReservationTool — 餐厅预订

方法上加 @Tool 注解，注解里用英文写清楚工具的用途、调用时机、参数要求。方法接收用户姓名、手机号、餐厅名、预约时间、人数、特殊需求等参数，校验手机号后写入 restaurant_reservation 表。

@Tool 描述里写英文的原因：通义千问对英文 Prompt 的工具调用理解更稳定，减少模型误判。

防止模型编造数据：@Tool 描述里明确写了 "Do NOT fabricate any data"，要求模型在参数不完整时先向用户询问，而不是自己填充假数据。

### RecommendationTool — 个性化推荐

三个工具方法配合使用：

| 方法 | 作用 | 返回 |
|------|------|------|
| getUserOrderHistory() | 查用户历史订单，统计常点菜系 | 菜系统计 JSON |
| getTopRatedRestaurants(category, minRating, limit) | 查高分餐厅，支持菜系/评分筛选 | 餐厅列表 JSON |
| getUserReviews(limit) | 查用户历史评价，分析口味偏好 | 评价列表 JSON |

推荐流程：用户问"推荐餐厅" → 模型先调 getUserOrderHistory 了解偏好 → 再调 getTopRatedRestaurants 查匹配餐厅 → 综合生成推荐理由。

---

# 五、MCP（Model Context Protocol）联网搜索

## 面试问：什么是 MCP，你项目里怎么用的？

MCP（Model Context Protocol）是 Anthropic 提出的开放协议，让 AI 模型可以通过标准化接口调用外部工具和数据源，类似 USB 接口的标准化思想。

**和 Function Calling 的区别**：Function Calling 的工具定义在本地代码里，需要自己实现；MCP 的工具由远端 MCP Server 提供，客户端只需连接，工具列表动态下发。

项目实现：在 McpConfig 里用 HttpMcpTransport 连接智谱 BigModel 的 MCP Server（SSE 长连接），创建 DefaultMcpClient，再用 McpToolProvider 包装后注册到 AiServices。连接后自动获取 webSearchPro、webSearchSogou 等联网搜索工具。

SSE（Server-Sent Events）长连接：MCP 用 SSE 保持与服务端的长连接，空闲时可能出现读超时和自动重连，日志里会有 WARN，这是正常现象，不是业务错误。

工具调用时机：由模型决定，用户问实时信息（如"今天有什么新餐厅"）时模型会调用联网搜索，普通问答不会调用。

---

# 六、多轮对话与会话记忆

## 面试问：多轮对话的上下文怎么管理的？

问题：HTTP 是无状态的，每次请求都是独立的，模型不知道上一轮说了什么。

方案：MessageWindowChatMemory + 自定义 RedisChatMemoryStore。

MessageWindowChatMemory 是滑动窗口记忆，maxMessages(10) 保留最近10条消息（包含用户消息和 AI 回复），超出后自动滑动丢弃最早的消息，防止 Prompt 过长超出模型 Token 限制。

RedisChatMemoryStore 实现：存储时把消息列表用 ChatMessageSerializer 序列化为 JSON，写入 Redis（key 是 chat:memory:{memoryId}，TTL 1天）；读取时用 ChatMessageDeserializer 反序列化回消息列表。

**为什么用 Redis 而不是内存？**

内存存储在服务重启后丢失，多实例部署下不同实例无法共享同一用户的会话。Redis 持久化解决了这两个问题。

## 遇到的坑：消息顺序异常导致模型错乱

问题：某些会话里模型出现上下文理解异常，但 Redis 数据是完整的。

排查：打印 Redis 中存储的消息顺序，发现从 Redis 恢复后，第一条非系统消息不是 UserMessage，而是 AI 回复或工具结果。

根本原因：LangChain4j 要求消息顺序满足协议约束——SystemMessage 可以在前，但第一条真正参与对话的非系统消息必须是 UserMessage。顺序不对时模型会出现上下文理解异常。

修复：在 RedisChatMemoryStore 里加 fixMessageOrder 逻辑，恢复消息后检查并修正顺序，保证第一条非系统消息是用户消息。

---

# 七、结构化输出（AI 评价审核 & 餐厅摘要）

## 面试问：怎么让模型返回结构化数据而不是自由文本？

LangChain4j 支持直接把模型输出反序列化为 Java 对象，只需把接口返回值声明为对应类型，框架自动处理 JSON 解析。

### AI 评价审核（ReviewAnalysisService）

接口方法返回值是 ReviewAnalysisResult 对象，@SystemMessage 里写清楚审核规则：文字与评分不一致返回 MANUAL_REVIEW，内容正常返回 APPROVE，含广告/辱骂/色情返回 REJECT，同时提取3-5个情感标签。方法参数用 @UserMessage 传评价内容，用 @V("rating") 传评分。

审核结果三种状态：APPROVE 通过正常发布，REJECT 拒绝含违规内容，MANUAL_REVIEW 人工审核（文字与评分矛盾，如5星但内容全是差评）。

异步处理：评价提交后不同步等待 AI 审核结果，而是发 RabbitMQ 消息，由 ReviewAuditConsumer 异步消费，避免用户等待。

### AI 餐厅摘要（RestaurantSummaryService）

接口方法返回值是 RestaurantSummary 对象，@SystemMessage 里要求生成 summary（1-2句总结，50字以内）、highlights（3-5个亮点）、warnings（1-3个注意事项）三个字段，并给出示例 JSON 格式约束输出。

缓存策略：生成的摘要缓存到 Redis（restaurant:ai:summary:{id}，24小时），避免每次查询都调用 AI 接口，节省 Token 费用。

---

# 八、AI 模块整体架构

## 面试问：你的 AI 模块是怎么设计的？

整体数据流：

用户请求 GET /ai/chat → ChatController → AiHelperService.chat() → LangChain4j AiServices 代理自动处理以下流程：从 RedisChatMemoryStore 加载历史消息（多轮记忆）→ ContentRetriever 检索相关知识（RAG）→ 判断是否需要调用工具（FoodReservationTool、RecommendationTool、McpToolProvider 三选一或不调用）→ 组装完整 Prompt（系统提示 + 历史消息 + RAG内容 + 用户消息）→ 调用通义千问 ChatModel → 返回 String。

**三类 AI 服务的职责分离**：

| 服务 | 能力 | 原因 |
|------|------|------|
| AiHelperService | 对话+RAG+工具+MCP | 主聊天入口，功能最全 |
| ReviewAnalysisService | 仅结构化输出 | 只需分析评价，不需要记忆和工具 |
| RestaurantSummaryService | 仅结构化输出 | 只需生成摘要，不需要记忆和工具 |

三个服务都通过 AiServiceFactory 用 AiServices.builder() 构建，按需挂载不同能力，不是所有服务都需要全部功能。

---

# 九、高频追问

## Q：RAG 和 Fine-tuning（微调）有什么区别，什么时候用哪个？

| 维度 | RAG | Fine-tuning |
|------|-----|------------|
| 成本 | 低，只需向量化知识库 | 高，需要大量标注数据和算力 |
| 知识更新 | 实时，更新知识库即可 | 慢，需要重新训练 |
| 适用场景 | 私有知识问答、文档检索 | 改变模型行为/风格/专业能力 |
| 幻觉风险 | 低，有检索内容作为依据 | 较高，依赖训练数据质量 |

项目选 RAG 而不是微调，原因是：美食知识会更新，RAG 更新成本低；不需要改变模型的基础能力，只需要补充业务知识。

## Q：向量数据库是什么，为什么用 Redis 而不是专用向量库？

向量数据库存储文本的向量表示（高维浮点数组），支持相似度搜索（余弦相似度/欧氏距离）。

项目用 Redis 的 RedisEmbeddingStore，原因是：项目已经依赖 Redis，不引入新中间件；知识库规模小（美食知识 txt 文件），Redis 向量搜索性能足够。生产环境大规模场景可以换 Milvus、Pinecone 等专用向量库。

## Q：Token 是什么，怎么控制 Token 消耗？

Token 是模型处理文本的基本单位，大约4个英文字符或1.5个中文字符算1个 Token。Token 数量直接影响 API 费用和响应速度。

项目里的 Token 控制手段：maxMessages(10) 限制历史消息数量防止上下文过长；minScore(0.75) 过滤低相关性 RAG 内容减少无效 Token；maxResults(5) RAG 最多取5个片段；餐厅摘要缓存24小时避免重复调用 AI 接口。

## Q：模型幻觉是什么，你项目里怎么应对的？

幻觉是指模型生成看似合理但实际错误的内容。

项目里的应对：RAG 让模型基于检索到的真实内容回答，而不是凭空生成；@Tool 描述里禁止编造（"Do NOT fabricate any data"），要求模型在数据不足时向用户询问；结构化输出用严格的 @SystemMessage 约束输出格式，减少自由发挥空间。

## Q：流式输出和非流式输出的区别？

非流式：等模型生成完整回答后一次性返回，用户等待时间长。流式：模型边生成边返回（SSE/WebSocket），用户看到逐字输出效果。

项目里 AiHelperService 同时声明了两个方法：chat() 返回 String（非流式），chatStream() 返回 TokenStream（流式）。AiServiceFactory 里同时注入了 chatModel 和 streamingChatModel，两种模式都支持。

## Q：System Prompt 的作用是什么？

System Prompt 是给模型的"角色设定"，在每次对话开始前注入，告诉模型它是谁、应该怎么回答、有哪些限制。

项目里用 @SystemMessage(fromResource = "system.txt") 从文件加载，内容包括 AI 助手的角色定位、回答风格、业务边界等。放在文件里而不是硬编码，方便修改而不需要重新编译。

---

# 十、面试总结（一段话版本）

> 我项目里 AI 模块基于 LangChain4j 构建，核心是一个集成了 RAG、Function Calling、MCP 联网搜索和 Redis 多轮记忆的 AI 助手。RAG 用通义千问嵌入模型把美食知识向量化存入 Redis，用户提问时检索相关片段增强回答质量；Function Calling 实现了餐厅预订和个性化推荐两个本地工具，推荐时先查用户历史订单分析偏好，再查高分餐厅匹配；MCP 接入智谱联网搜索，处理实时信息需求。除了主聊天服务，还有两个独立的 AI 服务：评价审核（异步 MQ 触发，返回 APPROVE/REJECT/MANUAL_REVIEW）和餐厅摘要生成（结果缓存24小时）。遇到过一个典型问题：Redis 恢复消息后顺序不满足 LangChain4j 协议约束，导致模型上下文错乱，最后在 RedisChatMemoryStore 里加了顺序修复逻辑解决。
