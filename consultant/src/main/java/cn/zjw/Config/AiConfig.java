package cn.zjw.config;


import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import cn.zjw.repository.RedisChatMemoryStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 模块核心配置类
 *
 * 负责创建 LangChain4j 所需的基础设施 Bean：
 *   1. EmbeddingStore   —— 向量数据库（存储知识库的语义向量）
 *   2. ContentRetriever —— RAG 检索器（用问题去向量库里找相关知识）
 *   3. ChatMemoryProvider —— 多会话记忆工厂（为每个 memoryId 创建独立的记忆窗口）
 *
 * 同时在应用启动时自动执行知识库初始化（@PostConstruct）。
 */
@Configuration
@Slf4j
public class AiConfig {
    
    /**
     * 嵌入模型（Embedding Model）
     *
     * 由 langchain4j-open-ai-spring-boot-starter 根据 application.yaml 中
     * langchain4j.open-ai.embedding-model 配置自动创建并注入，无需手动定义 Bean。
     * 用途：把文本（知识库文档 / 用户问题）转换成高维向量，向量相似度越高说明语义越接近。
     */
    @Autowired
    private EmbeddingModel embeddingModel;


    //用于加载classpath:content/*.txt 文件
    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Value("${spring.data.redis.host}")
    private String redisHost;


    @Value("${spring.data.redis.port:6380}")
    private int redisPort;

    /**
     * Bean 1：Redis 向量数据库（EmbeddingStore）
     *
     * 向量数据库与普通数据库的区别：
     *   普通 DB 按字段值精确匹配；向量 DB 按语义相似度检索。
     *   例如查询"怎么做红烧肉"，可以找到文档里"家常红烧肉做法"相关的段落，
     *   即使关键词不完全重合。
     *
     * 使用 Redis Stack（redis-stack-server 镜像）的 RediSearch 模块实现向量搜索，
     * 普通 Redis 镜像不支持向量索引。
     *
     * indexName：向量索引名称，首次调用 build() 时若不存在会自动创建。
     * dimension：向量维度，必须与 embedding-model 输出维度一致。
     *            text-embedding-v3 模型的默认输出维度是 1536。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(){
        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .indexName("food-knowledge-index")
                .dimension(1536)
                .build();
    }

    /**
     * Bean 2：RAG 内容检索器（ContentRetriever）
     *
     * RAG（Retrieval-Augmented Generation，检索增强生成）的核心组件。
     * 工作流程：
     *   用户问题 → embeddingModel 向量化 → 在 embeddingStore 中检索最相似的文本片段
     *   → 把检索到的片段拼入 Prompt → 发送给大模型生成回答
     *
     * 这样做的好处：AI 不再只依赖训练数据，而是能基于你提供的私有知识库回答问题，
     * 减少"幻觉"（AI 编造不存在的内容）。
     *
     * minScore(0.5)：相似度阈值，低于 0.5 的片段不返回，过滤掉不相关内容。
     * maxResults(3)：最多返回 3 个最相关的文本片段，避免 Prompt 过长。
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore){
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(0.5)
                .maxResults(3)
                .build();
                
    }

    /**
     * Bean 3：多会话记忆工厂（ChatMemoryProvider）
     *
     * ChatMemoryProvider 是一个函数式接口：接收 memoryId，返回一个 ChatMemory 实例。
     * @AiService 在处理请求时，会用请求携带的 memoryId 调用这个工厂方法，
     * 获取（或创建）该会话专属的记忆对象。
     *
     * MessageWindowChatMemory：滑动窗口记忆，只保留最近 N 条消息。
     *   maxMessages(20)：超过 20 条时，自动删除最早的消息，防止 token 超限。
     *   chatMemoryStore(redisChatMemoryStore)：指定持久化后端为 Redis，
     *   这样即使服务重启，历史消息也不会丢失。
     *
     * 不同 memoryId 的对话完全隔离，互不干扰——多用户并发没有问题。
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore redisChatMemoryStore){
        return chatMemoryId ->MessageWindowChatMemory.builder()
                .id(chatMemoryId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }
    
    /**
     * 知识库初始化（应用启动时自动执行）
     *
     * @PostConstruct：Spring 容器完成所有 Bean 注入后，立即执行此方法（只执行一次）。
     *
     * 流程：
     *   1. 扫描 resources/content/ 目录下所有 .txt 文件
     *   2. DocumentSplitters.recursive(500, 100)：
     *      把每个文件切成最长 500 字符的文本块，相邻块有 100 字符的重叠，
     *      重叠是为了防止关键信息恰好被切断在两个块的边界处。
     *   3. embeddingModel.embedAll()：调用通义千问嵌入接口，批量把文本块向量化。
     *   4. store.addAll()：把向量和原文一起存入 Redis 向量库。
     *
     * 注意：每次启动都会重新向量化并写入，如果知识库内容没变，
     * 建议后期优化为检查索引是否已存在，避免重复写入。
     */
    @PostConstruct
    public void initKnowledgeBase(){
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:content/*.txt");
            if(resources.length == 0){
                log.warn("content/ 目录下没有找到知识库文件");
                return;
            }
            DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
            //向量存储,store是RedisEmbeddingStore的实例
            EmbeddingStore<TextSegment> store = embeddingStore();

            for(Resource resource : resources){
                //读取文件内容
                String content=new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                
                Document document = Document.from(content,Metadata.from("source", resource.getFilename()));
                //切分文档
                List<TextSegment> segments = splitter.split(document);
                //向量化
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                //向量存储
                store.addAll(embeddings,segments);
                log.info("向量化完成：{} ({} 个片段)", resource.getFilename(), segments.size());
            }
            log.info("知识库初始化完成，文档数量:{}",resources.length);
        } catch (Exception e) {
            log.error("知识库初始化失败",e);
        }
    }

}
