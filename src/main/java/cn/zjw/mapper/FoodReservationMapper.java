package cn.zjw.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import cn.zjw.pojo.FoodReservation;

@Mapper
public interface FoodReservationMapper {

    //1.添加餐厅预订信息
    @Insert("insert into restaurant_reservation(name, phone, reservation_time, restaurant_name, people_count, special_request) " +
            "values(#{name}, #{phone}, #{reservationTime}, #{restaurantName}, #{peopleCount}, #{specialRequest})")
    void insert(FoodReservation reservation);

    //2.根据手机号查询餐厅预订信息
    @Select("select * from restaurant_reservation where phone = #{phone}")
    FoodReservation findByPhone(String phone);
}