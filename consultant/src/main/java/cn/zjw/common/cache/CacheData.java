package cn.zjw.common.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 逻辑过期缓存包装类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheData<T> {
    private T data;
    private LocalDateTime expireTime;
}
