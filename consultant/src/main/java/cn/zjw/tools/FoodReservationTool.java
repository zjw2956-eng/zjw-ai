package cn.zjw.tools;

import cn.zjw.pojo.FoodReservation;
import cn.zjw.service.FoodReservationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FoodReservationTool {
    @Autowired
    private FoodReservationService foodReservationService;

    @Tool("预订餐厅服务")
    //1.工具方法:添加餐厅预订信息
    public String reserveRestaurant(
        @P("姓名") String name,
        @P("联系方式") String phone,
        @P("预订时间,格式为:yyyy-MM-dd'T'HH:mm") String reservationTime,
        @P("餐厅名称") String restaurantName,
        @P("人数") Integer peopleCount,
        @P("特殊要求") String specialRequest
    ){
        FoodReservation reservation = new FoodReservation(
            null,
            name,
            phone,
            LocalDateTime.parse(reservationTime),
            restaurantName,
            peopleCount,
            specialRequest
        );
        foodReservationService.insert(reservation);
        return "餐厅预订成功！";
    }

    //2.工具方法:查询餐厅预订信息
    @Tool("根据手机号查询餐厅预订信息")
    public String findReservation(
        @P("联系方式") String phone
    ){
        FoodReservation reservation = foodReservationService.findByPhone(phone);
        if (reservation == null) {
            return "未查询到该手机号的预订信息";
        }
        return String.format("预订信息：\n姓名：%s\n手机号：%s\n预订时间：%s\n餐厅名称：%s\n人数：%d\n特殊要求：%s",
                reservation.getName(),
                reservation.getPhone(),
                reservation.getReservationTime(),
                reservation.getRestaurantName(),
                reservation.getPeopleCount(),
                reservation.getSpecialRequest());
    }
}