package cn.zjw.common.model;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageParams {


    //当前页码
    @Min(value = 1, message = "当前页码不能小于1")
    public Integer current = 1;

    //每页记录数
    @Min(value = 1, message = "每页记录数不能小于1")
    @Max(value = 100, message = "每页记录数不能超过100")
    public Integer pageSize = 5;
}
 