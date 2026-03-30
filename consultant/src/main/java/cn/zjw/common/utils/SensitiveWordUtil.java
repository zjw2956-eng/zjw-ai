package cn.zjw.common.utils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import cn.hutool.dfa.WordTree;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SensitiveWordUtil {

    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    private static final String SENSITIVE_WORDS_KEY = "sensitive:words";

    private WordTree wordTree;


    /**
     * 启动时初始化敏感词树
     */
    @PostConstruct
    public void init(){
        log.info("初始化敏感词树");
        //从redis加载敏感词库
        Set<Object> rawWords=redisTemplate.opsForSet().members(SENSITIVE_WORDS_KEY);
        Set<String> words;
        if(rawWords==null || rawWords.isEmpty() ){
            //首次启动，使用默认词库
            words=getDefaultSensitiveWords();
            log.info("使用默认敏感词库，包含{}个敏感词",words.size());
            redisTemplate.opsForSet().add(SENSITIVE_WORDS_KEY,words.toArray(new String[0]));
        }else{
            //转换为String类型
            words=rawWords.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
        }
        //构建DFA树
        wordTree = new WordTree();
        wordTree.addWords(words);
        log.info("敏感词树初始化完成，包含{}个敏感词",words.size());
    }


    /**
     * 检查文本是否包含敏感词
     * @param text
     * @return 包含的敏感词列表,如果为空则表示不包含敏感词
     */
    public List<String> check(String text){
        return wordTree.matchAll(text,-1,false,false);
    }


    /**
     * 默认词库，简单定义一个敏感词库
     * @return
     */
    private Set<String> getDefaultSensitiveWords(){
        return Set.of("傻逼", "垃圾", "骗子", "黑店",
              "加微信", "联系方式", "广告"
              // ... 更多敏感词
            );
    }
    
}
