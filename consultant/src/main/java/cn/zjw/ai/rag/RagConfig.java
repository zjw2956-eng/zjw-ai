package cn.zjw.ai.rag;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RagConfig {

    @Autowired
    private EmbeddingModel qwenEmbeddingModel;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    //Bean1：向量数据库
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(){
        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .indexName("food-knowledge-index")
                .dimension(1536)
                .build();
    }

    //Bean2:检索器（只负责创建，不加载数据）
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore) throws IOException {
        //RAG
        //自定义内容检索器
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(qwenEmbeddingModel)
                .embeddingStore(embeddingStore)
                .maxResults(5)//最多5个检索结果
                .minScore(0.75)//过滤掉分数小于0.75的结果
                .build();
        return contentRetriever;
    }
}
