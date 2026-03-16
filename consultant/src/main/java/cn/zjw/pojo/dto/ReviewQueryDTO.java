package cn.zjw.pojo.dto;

import lombok.Data;

@Data
public class ReviewQueryDTO {
    private Integer current = 1;
    private Integer pageSize = 10;
    private Long restaurantId;     // 查某餐厅的评价列表（必传）
}