package cn.zjw.service.impl;

import cn.zjw.mapper.DishMapper;
import cn.zjw.pojo.entity.Dish;
import cn.zjw.service.DishService;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@Service
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    
}
