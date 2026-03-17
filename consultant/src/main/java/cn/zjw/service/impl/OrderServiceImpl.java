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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import org.springframework.transaction.annotation.Transactional;

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
    
    // TODO: [RabbitMQ] 集成后需加 @Transactional，配合发布者确认机制保证消息可靠性
    // TODO: [幂等性] 在 orderMapper.insert() 前加幂等校验，防止用户重复提交
    //   方案：Redis SETNX 检查 key: idempotent:order:{userId}:{idempotentToken}
    //   存在 → 已处理，直接返回；不存在 → 写入并继续
    //   兜底：order_info 表对 (user_id, restaurant_id, reservation_time) 加唯一索引
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
        orderMapper.insert(orderInfo); // ← 写库成功
        // TODO: [RabbitMQ] 发送订单创建通知消息
        //   交换机: order.exchange
        //   routing key: order.create
        //   消息体: { orderNo, userId, restaurantId, reservationTime }
        //   消费者: 发送短信/推送通知用户和餐厅
        // TODO: [RabbitMQ] 发送订单超时延迟消息（30分钟后触发自动取消）
        //   交换机: order.delay.exchange（需配置死信队列 DLX 实现延迟）
        //   routing key: order.timeout
        //   消息体: { orderNo }
        //   消费者: 检查订单状态，若仍为 PENDING 则更新为 CANCELLED
    }

    @Override
    public Page<OrderVO> listOrders(Integer current, Integer pageSize,Integer status){
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        //只查询当前用户自己的订单
        Long userId=UserContext.getCurrentUserId();
        wrapper.eq(OrderInfo::getUserId,userId);
        
        if(status != null){
            wrapper.eq(OrderInfo::getStatus,status);
        }
        wrapper.eq(OrderInfo::getIsDeleted,0).orderByDesc(OrderInfo::getUpdateTime);
        Page<OrderInfo> Firstpage=this.page(new Page<>(current,pageSize),wrapper);

        //转换为VO列表VO
        List<OrderInfo> orderList=Firstpage.getRecords();
        //空分页判断
        if (orderList.isEmpty()) {
            Page<OrderVO> emptyPage = new Page<>(Firstpage.getCurrent(), Firstpage.getSize(), 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo){
        LambdaQueryWrapper<OrderInfo> wrapper=new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getIsDeleted,0).eq(OrderInfo::getOrderNo,orderNo);
        //根据订单号查询订单
        OrderInfo orderInfo=orderMapper.selectOne(wrapper);
        //如果订单不存在，返回错误
        if(orderInfo==null){
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(),"订单不存在");
        }
        //校验是否为当前用户
        if (!UserContext.getCurrentUserId().equals(orderInfo.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(),"订单不属于当前用户，无权取消");
        }
        //校验订单状态
        if (!orderInfo.getStatus().equals(OrderStatus.PENDING.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),"当前订单状态不支持取消");
        }
        //更新订单状态
        orderInfo.setStatus(OrderStatus.CANCELLED.getCode());

        //更新订单
        orderMapper.updateById(orderInfo);

        //删除缓存
        redisTemplate.delete(Constants.REDIS_ORDER_DETAIL+orderNo);
        // TODO: [RabbitMQ] 取消成功后发送通知消息，告知用户订单已取消
        //   交换机: order.exchange，routing key: order.cancel
        //   消息体: { orderNo, userId }
    }
}
