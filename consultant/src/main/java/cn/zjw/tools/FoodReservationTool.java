package cn.zjw.tools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.PhoneUtil;
import cn.hutool.json.JSONUtil;
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
            //校验手机号
            if(!PhoneUtil.isMobile(phone)){
                return "手机号格式错误";
            }
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
    

    /**
     * 查询预订信息
     * @return 预订信息
     */
    @Tool("查询餐厅预定信息，需要提供预订人姓名、电话")
    public String findReservation(
        @P("预订人姓名") String name,
        @P("预订人电话") String phone) {
        try {
            //校验手机号
            if(!PhoneUtil.isMobile(phone)){
                return "手机号格式错误";
            }
            LambdaQueryWrapper<Reservation> wrapper=new LambdaQueryWrapper<>();
            wrapper.eq(Reservation::getName, name)
                    .eq(Reservation::getPhone, phone)
                    .orderByDesc(Reservation::getCreateTime)
                    .last("limit 1");
            Reservation reservation = reservationMapper.selectOne(wrapper);
            if(reservation==null){
                return "没有找到预订信息";
            }
            return "预订信息：" + JSONUtil.toJsonStr(reservation);
        } catch (Exception e) {
            return "查询失败：" + e.getMessage();
        }
    }
}
