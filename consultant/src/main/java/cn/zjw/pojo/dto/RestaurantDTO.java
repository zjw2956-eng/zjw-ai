package cn.zjw.pojo.dto;

import lombok.Data;

/**
 * 新增餐厅DTO
 */
@Data   
public class RestaurantDTO {
    private Long id;
    private String name;
    private String category;
    private String address;
    private BigDecimal avgPrice;
    private String businessHours;
    private String images;
    private String description;
    private Integer status;  
}
