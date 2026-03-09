package cn.zjw.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 餐厅实体
 */
@Data
@TableName("restaurant")
public class Restaurant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal avgPrice;
    private BigDecimal rating;
    private String phone;
    private String businessHours;
    private String images;
    private String description;
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
