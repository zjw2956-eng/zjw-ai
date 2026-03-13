package cn.zjw.common.model;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PageParams {

    //默认起始页码
    public static final Long DEFAULT_PAGE_CURRENT = 1L;
    //默认每页记录数
    public static final Long DEFAULT_PAGE_SIZE = 10L;

    //当前页码
    @Schema(description = "当前页码", example = "1")
    private Long pageNo=DEFAULT_PAGE_CURRENT;

    //每页记录数
    @Schema(description = "每页记录数", example = "10")
    private Long pageSize=DEFAULT_PAGE_SIZE;
}

