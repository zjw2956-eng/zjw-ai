package cn.zjw.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import cn.zjw.pojo.entity.Reservation;

@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
    
}
