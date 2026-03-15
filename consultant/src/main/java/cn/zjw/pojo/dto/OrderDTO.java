package cn.zjw.pojo.dto;
import java.time.LocalDateTime;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 订单DTO
 */
@Data
public class OrderDTO {
    @NotBlank(message = "餐厅ID不能为空")
    private Long restaurantId;
    private LocalDateTime reservationTime;
    private Integer peopleCount;
    private String contactName;
    private String contactPhone;
    private String specialRequest;
}
