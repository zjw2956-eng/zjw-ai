package cn.zjw.pojo.dto;

import cn.zjw.common.model.PageParams;
import lombok.Data;

/**
 * 订单分页查询DTO
 */
@Data
public class OrderQueryDTO extends PageParams{

    //订单状态
    private Integer status;

}
 
