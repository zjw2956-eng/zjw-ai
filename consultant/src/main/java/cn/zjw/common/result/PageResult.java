package cn.zjw.common.result;
import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 分页返回结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> implements Serializable{
    private Long total;
    private List<T> records;
    private Long pageNum;
    private Long pageSize;
}
