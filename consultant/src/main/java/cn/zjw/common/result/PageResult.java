package cn.zjw.common.result;
import java.util.List;
import java.io.Serializable;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
