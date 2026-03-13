package cn.zjw.pojo.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 新增/修改餐厅DTO
 */
@Data
public class RestaurantDTO {
    /** 修改时必填，新增时不填 */
    private Long id;
    private String name;
    private String category;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal avgPrice;
    private String phone;
    private String businessHours;
    private String images;
    private String description;
    private Integer status;
}
