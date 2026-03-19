package cn.zjw.config;


import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
//import dev.langchain4j.service.memory.ChatMemoryProvider;
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

@Configuration
@Slf4j
public class AiConfig {
    
    
    // EmbeddingModel 由 langchain4j-open-ai-spring-boot-starter 根据
    // application.yaml 中的 langchain4j.open-ai.embedding-model 自动配置，直接注入即可
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
     * Redis EmbeddingStore向量数据库
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
     * RAG内容检索器
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
}
