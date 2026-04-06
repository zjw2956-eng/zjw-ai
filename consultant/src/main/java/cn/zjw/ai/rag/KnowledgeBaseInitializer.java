package cn.zjw.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
@Slf4j
@Component
public class KnowledgeBaseInitializer {

    private static final String INIT_KEY = "knowledge:initialized";
    private static final Duration TTL = Duration.ofDays(300);  // 300天过期

    @Autowired
    private EmbeddingModel qwenEmbeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


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
            //1.检查是否已经初始化
            if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(INIT_KEY))){
                log.info("知识库已初始化，跳过加载");
                return;
            }
            log.info("开始初始化知识库......");
            //2.加载文档
            PathMatchingResourcePatternResolver resolver=new PathMatchingResourcePatternResolver();
            Resource[] resources=resolver.getResources("classpath:content/*.txt");
            if(resources.length==0){
                log.warn("content/ 目录下没有找到 .txt 文件");
                return;
            }
            List<Document> documents=new ArrayList<>();
            for(Resource resource:resources){
                Document doc=FileSystemDocumentLoader.loadDocument(resource.getFile().toPath());
                documents.add(doc);
                log.info("加载文档：{}",resource.getFilename());
            }
            //3.切分文档
            //4.向量化并存储
            EmbeddingStoreIngestor ingestor=EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(500,100))
                    .embeddingStore(embeddingStore)
                    .embeddingModel(qwenEmbeddingModel)
                    .build();
            ingestor.ingest(documents);
            //5.标记已初始化
            stringRedisTemplate.opsForValue().set(INIT_KEY,"true",TTL);
            log.info("知识库初始化完成！文档数量：{}", resources.length);
        } catch (Exception e) {
            log.error("知识库初始化失败",e);
        }
    }
}
