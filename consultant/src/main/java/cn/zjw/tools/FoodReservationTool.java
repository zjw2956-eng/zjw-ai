package cn.zjw.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.zjw.mapper.ReservationMapper;
import cn.zjw.pojo.entity.Reservation;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

@Component
public class FoodReservationTool {

    @Autowired
    private ReservationMapper reservationMapper;
    /**
     * 预约餐厅
     * @return 预约结果
     */
    @Tool("预定餐厅，需要提供姓名、电话、餐厅名、日期时间、人数、特殊要求")
    public String reserveRestaurant(
            @P("预订人姓名") String name,
            @P("预订人电话") String phone,
            @P("餐厅名") String restaurantName,
            @P("预订日期时间，格式为yyyy-MM-dd HH:mm") String reservationTime,
            @P("用餐人数") int peopleCount,
            @P("特殊要求，没有则传空字符串") String specialRequest) {
        try {
            LocalDateTime reservationTimeObj = LocalDateTime.parse(reservationTime,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            Reservation reservation = new Reservation();
            reservation.setName(name);
            reservation.setPhone(phone);
            reservation.setRestaurantName(restaurantName);
            reservation.setReservationTime(reservationTimeObj);
            reservation.setPeopleCount(peopleCount);
            reservation.setSpecialRequest(specialRequest);
            reservationMapper.insert(reservation);               
            return "预约成功";
        } catch (Exception e) {
            return "预约失败：" + e.getMessage();
        }
    }
    
}
