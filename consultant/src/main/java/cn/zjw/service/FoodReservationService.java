package cn.zjw.service;

import org.springframework.beans.factory.annotation.Autowired;

import cn.zjw.mapper.FoodReservationMapper;
import cn.zjw.pojo.FoodReservation;
import org.springframework.stereotype.Service;

@Service
public class FoodReservationService {

    @Autowired
    private FoodReservationMapper foodReservationMapper;

    //1.添加餐厅预订信息方法
    public void insert(FoodReservation reservation) {
        foodReservationMapper.insert(reservation);
    }

    //2.查询餐厅预订信息方法（根据手机号）
    public FoodReservation findByPhone(String phone) {
        return foodReservationMapper.findByPhone(phone);
    }
}