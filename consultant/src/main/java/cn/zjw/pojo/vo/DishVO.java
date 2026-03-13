package cn.zjw.pojo.vo;

import java.math.BigDecimal;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DishVO {
    private Long id;
    private Long restaurantId;
    private String name;
    private BigDecimal price;
    private String image;
    private String description;
    private String category;
    private Integer isRecommend;
    private Integer status;
}
