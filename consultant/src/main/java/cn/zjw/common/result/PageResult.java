package cn.zjw.common.result;

import lombok.Data;

/**
 * 分页返回结果
 */
@Data
public class PageResult<T> {
    private Long total;
    private T records;
}
