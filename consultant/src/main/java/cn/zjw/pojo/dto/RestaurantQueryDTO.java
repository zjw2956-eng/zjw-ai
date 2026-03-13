package cn.zjw.pojo.dto;

import lombok.Data;

@Data
public class RestaurantQueryDTO {
    private Integer current =1;
    private Integer size =10;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minRating;
}
