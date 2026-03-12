package cn.zjw.common.result;
import java.util.List;
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
public class PageResult<T> {
    private Long total;
    private List<T> records;

    public static <T> PageResult<T> of(Page<T> page){
        PageResult<T> pageResult=new PageResult();
        pageResult.setTotal(page.getTotal());
        pageResult.setRecords(page.getRecords());
        return pageResult;
    }
}
