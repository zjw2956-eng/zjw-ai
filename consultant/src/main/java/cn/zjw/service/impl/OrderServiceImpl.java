package cn.zjw.service.impl;

import cn.zjw.service.OrderService;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.zjw.common.context.UserContext;
import cn.hutool.core.util.PhoneUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;
import cn.zjw.common.constant.Constants;
import cn.zjw.pojo.dto.OrderDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import cn.hutool.core.bean.BeanUtil;
import cn.zjw.common.enums.OrderStatus;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.vo.OrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单Service实现
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderInfo> implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RestaurantMapper restaurantMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Override
    public void createOrder(OrderDTO dto){
        OrderInfo orderInfo=new OrderInfo();
        //用户上下文取用户ID
        Long userId=UserContext.getCurrentUserId();
        orderInfo.setUserId(userId);  // ✅ 设置用户ID
        //设置订单号
        String orderNo=Constants.ORDER_ID_PREFIX+System.currentTimeMillis()+userId.toString();
        orderInfo.setOrderNo(orderNo);
        //设置状态为待确认
        orderInfo.setStatus(OrderStatus.PENDING.getCode());

        BeanUtil.copyProperties(dto, orderInfo);
        orderMapper.insert(orderInfo);
    }

    @Override
    public Page<OrderVO> listOrders(Integer current, Integer pageSize,Integer status){
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        if(status != null){
            wrapper.eq(OrderInfo::getStatus,status);
        }
        wrapper.eq(OrderInfo::getIsDeleted,0).orderByDesc(OrderInfo::getUpdateTime);
        Page<OrderInfo> Firstpage=this.page(new Page<>(current,pageSize),wrapper);

        //转换为VO列表VO
        List<OrderInfo> orderList=Firstpage.getRecords();
        Set<Long> restaurantIds=orderList.stream()
                .map(OrderInfo::getRestaurantId)
                .collect(Collectors.toSet());
        //批量查询餐厅信息
        List<Restaurant> restaurants=restaurantMapper.selectBatchIds(restaurantIds);
        // 构建 restaurantId → Restaurant 的 Map
        Map<Long, Restaurant> restaurantMap = restaurants.stream()
                .collect(Collectors.toMap(Restaurant::getId, r -> r));
        List<OrderVO> voList= orderList.stream()
                            .map(order->{
                                OrderVO vo=BeanUtil.copyProperties(order,OrderVO.class);
                                //设置餐厅信息
                                Restaurant restaurant=restaurantMap.get(order.getRestaurantId());
                                if(restaurant!=null){
                                    vo.setRestaurantId(restaurant.getId());
                                    vo.setRestaurantName(restaurant.getName());
                                }
                                //设置状态描述
                                vo.setStatus(order.getStatus());
                                vo.setStatusDesc(OrderStatus.getDescByCode(order.getStatus()));

                                return vo;
                            })
                            .collect(Collectors.toList());

        Page<OrderVO> resultPage=new Page<>(
                Firstpage.getCurrent(),
                Firstpage.getSize(),
                Firstpage.getTotal()
        );
        resultPage.setRecords(voList);
        
        return resultPage;
    }

    @Override
    public OrderVO getOrderDetail(String orderNo){
        //查询缓存
        String json=redisTemplate.opsForValue().get(Constants.REDIS_ORDER_DETAIL+orderNo);
        if(json!=null){
            if (json.isEmpty()) {//缓存中为空字符串，返回null
                return null;
            }
            return JSONUtil.toBean(json,OrderVO.class);
        }

        //查询数据库
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo,orderNo);
        OrderInfo orderInfo=orderMapper.selectOne(wrapper);
        if (orderInfo==null) {
            //缓存中没有，返回null
            log.warn("订单不存在，orderNo={}",orderNo);
            //设置缓存过期时间为2分钟,防止缓存穿透
            redisTemplate.opsForValue().set(Constants.REDIS_ORDER_DETAIL+orderNo,"",Constants.REDIS_EMPTY_KEY_EXPIRE_TIME, TimeUnit.SECONDS);
            return null;
        }
        OrderVO orderVO=new OrderVO();
        BeanUtil.copyProperties(orderInfo, orderVO);
        orderVO.setStatusDesc(OrderStatus.getDescByCode(orderInfo.getStatus()));
        //餐厅信息
        Restaurant restaurant=restaurantMapper.selectById(orderInfo.getRestaurantId());
        if(restaurant!=null){
            orderVO.setRestaurantId(restaurant.getId());
            orderVO.setRestaurantName(restaurant.getName());
        }
        //缓存订单详情,过期时间为72小时
        redisTemplate.opsForValue().set(Constants.REDIS_ORDER_DETAIL+orderNo,JSONUtil.toJsonStr(orderVO),Constants.REDIS_EXPIRE_TIME, TimeUnit.SECONDS);
        return orderVO;
    }
}
