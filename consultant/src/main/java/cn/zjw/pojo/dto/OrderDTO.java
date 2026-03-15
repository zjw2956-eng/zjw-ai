package cn.zjw.pojo.dto;
import java.time.LocalDateTime;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 订单DTO
 */
@Data
public class OrderDTO {
    private Long restaurantId;
    private LocalDateTime reservationTime;
    private Integer peopleCount;
    private String contactName;
    private String contactPhone;
    private String specialRequest;
}
