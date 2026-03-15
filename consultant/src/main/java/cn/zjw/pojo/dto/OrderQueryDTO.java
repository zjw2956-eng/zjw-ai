package cn.zjw.pojo.dto;

import lombok.Data;

/**
 * 订单分页查询DTO
 */
@Data
public class OrderQueryDTO {

    private Integer current=1;
    private Integer pageSize=10;

    //订单状态
    private Integer status;

}
 
