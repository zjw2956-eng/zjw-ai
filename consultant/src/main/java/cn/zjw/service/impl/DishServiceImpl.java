package cn.zjw.service.impl;

import cn.zjw.mapper.DishMapper;
import cn.zjw.pojo.entity.Dish;
import cn.zjw.service.DishService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

}
