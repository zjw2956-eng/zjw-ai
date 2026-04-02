package cn.zjw.pojo.dto;

import lombok.Data;
import java.math.BigDecimal;

import cn.zjw.common.model.PageParams;

@Data
public class RestaurantQueryDTO extends PageParams{
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minRating;
}
