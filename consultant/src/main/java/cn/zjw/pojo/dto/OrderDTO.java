package cn.zjw.pojo.dto;
import java.time.LocalDateTime;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * 订单DTO
 */
@Data
public class OrderDTO {
    @NotNull(message = "餐厅ID不能为空")
    private Long restaurantId;
    @NotNull(message = "预订时间不能为空")
    @Future(message = "预订时间必须是未来时间")
    private LocalDateTime reservationTime;
    @NotNull(message = "用餐人数不能为空")
    @Min(value = 1, message = "用餐人数至少1人")
    @Max(value = 50, message = "用餐人数不超过50人")
    private Integer peopleCount;
    @NotBlank(message = "联系人姓名不能为空")
    private String contactName;
    @NotBlank(message = "联系人电话不能为空")
    private String contactPhone;
    private String specialRequest;
}
