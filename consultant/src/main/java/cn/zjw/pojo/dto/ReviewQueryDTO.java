package cn.zjw.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ReviewQueryDTO {
    @Min(value = 1, message = "页码不能小于1")
    private Integer current = 1;
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;
    @NotNull(message = "餐厅ID不能为空")
    private Long restaurantId; // 查某餐厅的评价列表（必传）

    private Integer rating; // 每个订单的评价评分（可选，1,2,3,4,5分），用来做评价筛选
}