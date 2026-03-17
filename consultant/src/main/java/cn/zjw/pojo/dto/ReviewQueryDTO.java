package cn.zjw.pojo.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class ReviewQueryDTO {
    private Integer current = 1;
    private Integer pageSize = 10;
    @NotNull(message = "餐厅ID不能为空")
    private Long restaurantId;     // 查某餐厅的评价列表（必传）

    private Integer rating;     // 每个订单的评价评分（可选，1,2,3,4,5分），用来做评价筛选
}