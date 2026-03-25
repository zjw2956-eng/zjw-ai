package cn.zjw.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.enums.DishStatus;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.CommonResult;
import cn.zjw.common.result.ResultCode;
import cn.zjw.mapper.DishMapper;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.dto.RestaurantDTO;
import cn.zjw.pojo.entity.Dish;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.vo.DishVO;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.RestaurantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 餐厅Service实现
 */
@Service
@Slf4j
public class RestaurantServiceImpl extends ServiceImpl<RestaurantMapper, Restaurant> implements RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DishMapper dishMapper;

    @Override
    public Page<RestaurantVO> listRestaurants(Integer current, Integer size,
        String category, BigDecimal minPrice,BigDecimal maxPrice, BigDecimal minRating) {
        LambdaQueryWrapper<Restaurant> wrapper = new LambdaQueryWrapper<Restaurant>()
            /**
             * 分页条件
             * 状态正常：营业中
             * 未删除：正常营业
             * 分类匹配：可选菜系
             * 价格范围：可选价格区间
             * 评分范围：可选评分区间
             * 排序：按评分降序
             */
                .eq(Restaurant::getStatus, Constants.RESTAURANT_STATUS_NORMAL)
                .eq(Restaurant::getIsDeleted, 0)
                .eq(StringUtils.hasText(category),Restaurant::getCategory,category)
                //价格范围 ge是大于等于，le是小于等于
                .ge(minPrice!=null,Restaurant::getAvgPrice,minPrice)
                .le(maxPrice!=null,Restaurant::getAvgPrice,maxPrice)
                .ge(minRating!=null,Restaurant::getRating,minRating)
                .orderByDesc(Restaurant::getRating);
        //关键步骤，执行分页查询，MyBatis-Plus提供的page方法
        //返回的 restaurantPage 里装好了：当前页数据列表 + 总记录数 + 总页数
        Page<Restaurant> restaurantPage = this.page(new Page<>(current, size), wrapper);

        /**
         * getRecords() → 拿出当前页的餐厅列表（List<Restaurant>）
         * BeanUtil.copyProperties → 把每个 Restaurant（数据库实体）复制成 RestaurantVO（返回给前端的对象）
         * collect(Collectors.toList()) → 把所有复制好的 RestaurantVO 收集成一个 List
         */
        List<RestaurantVO> voList = restaurantPage.getRecords().stream()
                .map(r -> BeanUtil.copyProperties(r, RestaurantVO.class))
                .collect(Collectors.toList());

        /**
         * 为什么不直接返回 restaurantPage？
         * 因为它是 Page<Restaurant> 类型，里面装的是实体，不是 VO。
         * 所以需要新建一个 Page<RestaurantVO>，然后把总数/页码这些信息从旧的搬过来。
         */
        Page<RestaurantVO> resultPage = new Page<>(
                restaurantPage.getCurrent(),
                restaurantPage.getSize(),
                restaurantPage.getTotal()
        );
        resultPage.setRecords(voList);

        return resultPage;
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
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "餐厅不存在");
        }
        //查询菜品
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
            .eq(Dish::getRestaurantId, id)
            .eq(Dish::getStatus, DishStatus.ON_SALE.getCode())
            .eq(Dish::getIsDeleted, 0)
            .orderByDesc(Dish::getIsRecommend);
        List<Dish> dishList= dishMapper.selectList(wrapper);

        List<DishVO> dishVOList=dishList.stream().map(d->BeanUtil.copyProperties(d, DishVO.class)).collect(Collectors.toList());
            
        // Entity → VO，写入缓存
        RestaurantVO restaurantVO = BeanUtil.copyProperties(restaurant, RestaurantVO.class);
        //设置菜品列表
        restaurantVO.setDishes(dishVOList);
        // TODO: [缓存一致性] 当菜品状态变更（上架/下架/新增/删除）时，需删除餐厅缓存
        //   涉及接口：DishService.addDish()、DishService.updateDishStatus() 等
        //   删除方式：stringRedisTemplate.delete(Constants.REDIS_RESTAURANT_KEY + restaurantId)
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(restaurantVO),
                Constants.REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
        return restaurantVO;
    }

    @Override
    public void addRestaurant(RestaurantDTO dto) {
        // TODO: 新增餐厅（需要管理员/商家权限）
        // 1. 权限校验（管理员或商家角色才能调用）
        // 2. 校验餐厅名称是否重复
        // 3. DTO → Entity
        // 4. 保存到数据库
    }

    @Override
    public void updateRestaurant(RestaurantDTO dto) {
        // TODO: 修改餐厅（需要管理员/商家权限）
        // 1. 权限校验（只有该餐厅的商家或管理员才能修改）
        // 2. 校验 dto.getId() 不为空
        // 3. 校验餐厅是否存在
        // 4. DTO → Entity，更新到数据库
        // 5. 删除该餐厅的 Redis 缓存，防止读到旧数据
    }

    @Override
    public List<Restaurant> getTopRatedRestaurants(String category, BigDecimal minRating, Integer limit) {
        // 查询高分餐厅（用于推荐）
        LambdaQueryWrapper<Restaurant> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(Restaurant::getIsDeleted,0)
            .eq(category!=null,Restaurant::getCategory,category)
            .ge(minRating!=null,Restaurant::getRating,minRating)
            .orderByDesc(Restaurant::getRating)
            .last("limit" + (limit==null?10:limit));
        return restaurantMapper.selectList(wrapper);
    }
}
