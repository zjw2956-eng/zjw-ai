package cn.zjw.pojo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodReservation {
    private Long id;
    private String name;
    private String phone;
    private LocalDateTime reservationTime;
    private String restaurantName;
    private Integer peopleCount;
    private String specialRequest;
    
}