package cn.zjw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.RestaurantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 餐厅Service实现
 */
@Service
@Slf4j
public class RestaurantServiceImpl implements RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Page<RestaurantVO> page(Integer pageNum, Integer pageSize, String category) {
        Page<Restaurant> entityPage = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Restaurant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(category != null && !category.isBlank(), Restaurant::getCategory, category)
                .eq(Restaurant::getStatus, Constants.RESTAURANT_STATUS_NORMAL)
                .orderByDesc(Restaurant::getRating);
        restaurantMapper.selectPage(entityPage, wrapper);

        // 将 Entity 列表转换为 VO 列表
        List<RestaurantVO> voList = entityPage.getRecords().stream()
                .map(r -> BeanUtil.copyProperties(r, RestaurantVO.class))
                .collect(Collectors.toList());

        // 构造返回的 Page<RestaurantVO>
        Page<RestaurantVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public RestaurantVO getRestaurantById(Long id) {
        // 先查 Redis 缓存
        String cacheKey = Constants.REDIS_RESTAURANT_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json != null) {
            return JSONUtil.toBean(json, RestaurantVO.class);
        }

        // 缓存未命中，查数据库
        Restaurant restaurant = restaurantMapper.selectById(id);
        if (restaurant == null) {
            throw new BusinessException(404, "餐厅不存在");
        }

        // Entity → VO，写入缓存
        RestaurantVO restaurantVO = BeanUtil.copyProperties(restaurant, RestaurantVO.class);
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(restaurantVO),
                Constants.REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
        return restaurantVO;
    }
}
