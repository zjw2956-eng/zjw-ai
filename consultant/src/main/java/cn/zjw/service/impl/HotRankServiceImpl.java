package cn.zjw.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;
import cn.zjw.common.constant.Constants;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.dto.HotRankItem;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.service.HotRankService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HotRankServiceImpl implements HotRankService {

    private static final int DEFAULT_LIMIT = 10;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RestaurantMapper restaurantMapper;

    @Override
    public void refreshHotRestaurantRank() {
        // 查询30天内已完成订单聚合
        List<HotRankItem> rankItems = orderMapper.selectHotRankLast30Days();
        // 清空旧榜单
        stringRedisTemplate.delete(Constants.HOT_RESTAURANT_RANK_KEY);
        if (rankItems == null || rankItems.isEmpty()) {
            log.info("热门榜单刷新完成，但无数据");
            return;
        }
        // 批量写入 ZSet
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (HotRankItem item : rankItems) {
            if (item.getRestaurantId() == null || item.getScore() == null) {
                continue;
            }
            tuples.add(ZSetOperations.TypedTuple.of(
                    String.valueOf(item.getRestaurantId()),
                    item.getScore().doubleValue()));
        }
        if (!tuples.isEmpty()) {
            stringRedisTemplate.opsForZSet().add(
                    Constants.HOT_RESTAURANT_RANK_KEY,
                    tuples);
        }
        log.info("热门榜单刷新完成，写入 {} 条", tuples.size());
    }

    @Override
    public List<Long> getHotRestaurantIds(int limit) {
        int size = limit > 0 ? limit : DEFAULT_LIMIT;
        Set<String> idSet = stringRedisTemplate.opsForZSet()
                .reverseRange(Constants.HOT_RESTAURANT_RANK_KEY, 0, size - 1);
        if (idSet == null || idSet.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(idSet.size());
        for (String idStr : idSet) {
            ids.add(Long.valueOf(idStr));
        }
        return ids;
    }

    @Override
    public List<RestaurantVO> getHotRestaurants(int limit) {
        List<Long> ids = getHotRestaurantIds(limit);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 1.批量查询餐厅
        List<Restaurant> restaurants = restaurantMapper.selectBatchIds(ids);
        if (restaurants == null || restaurants.isEmpty()) {
            return Collections.emptyList();
        }
        // 2.转VO并建立map
        // 用 BeanUtils 简化（需导入 org.springframework.beans.BeanUtils）
        Map<Long, RestaurantVO> voMap = restaurants.stream()
                .collect(Collectors.toMap(
                        Restaurant::getId,
                        r -> {
                            RestaurantVO vo = new RestaurantVO();
                            BeanUtil.copyProperties(r, vo); // 自动拷贝同名属性
                            return vo;
                        }));
        //按榜单顺序重排序返回
        List<RestaurantVO> result= new ArrayList<>();
        for(Long id:ids){
            RestaurantVO vo=voMap.get(id);
            if(vo!=null){
                result.add(vo);
            }
        }
        return result;
    }

}
