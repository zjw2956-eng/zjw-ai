package cn.zjw.pojo.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.*;


import lombok.Data;

/**
 * 餐厅预约
 */
@Data
@TableName("restaurant_reservation")
public class Reservation implements Serializable  {
    @TableId(type = IdType.AUTO)
    private Long id; 

    // 预约人姓名
    private String name;
    // 预约人电话
    private String phone;
    // 预约餐厅名
    private String restaurantName;
    // 预约日期时间
    private LocalDateTime reservationTime;
    // 用餐人数
    private int peopleCount;
    // 特殊要求
    private String specialRequest;
    // 创建时间
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;


}
