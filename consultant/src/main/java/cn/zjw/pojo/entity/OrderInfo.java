package cn.zjw.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单实体
 */
@Data
@TableName("order_info")
public class OrderInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;//订单号
    private Long userId;//用户ID
    private Long restaurantId;//餐厅ID
    private LocalDateTime reservationTime;//预订时间
    private Integer peopleCount;//用餐人数
    private String contactName;//联系人
    private String contactPhone;//联系电话
    private String specialRequest;//特殊要求
    private Integer status;//状态

    @TableLogic
    private Integer isDeleted;//逻辑删除

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;//创建时间

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
