package cn.zjw.pojo.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 订单VO
 */
@Data
public class OrderVO {
    private String orderNo;
    private LocalDateTime reservationTime;
    
    private Integer peopleCount;
    private String contactName;
    private String contactPhone;
    private String specialRequest;
    private LocalDateTime createTime;

    private Long restaurantId;
    private String restaurantName;

    private Integer status;//状态 0：待确认 1：已确认 2:已完成 3：已取消
    private String statusDesc;//状态描述
}
