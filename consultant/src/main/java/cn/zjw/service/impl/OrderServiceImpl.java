package cn.zjw.service.impl;

import cn.zjw.service.OrderDelayService;
import cn.zjw.service.OrderService;
import cn.zjw.mapper.OrderMapper;
import cn.zjw.pojo.entity.OrderInfo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.zjw.common.context.UserContext;
import cn.hutool.core.util.PhoneUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;

import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;
import cn.zjw.pojo.dto.OrderDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import cn.hutool.core.bean.BeanUtil;
import cn.zjw.common.enums.OrderStatus;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.mq.event.OrderCreatedEvent;
import cn.zjw.pojo.vo.OrderVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import cn.zjw.common.utils.OrderNoGenerator;

import org.springframework.transaction.annotation.Transactional;

/**
 * 订单Service实现
 */
@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderInfo> implements OrderService {
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RestaurantMapper restaurantMapper;

    @Autowired
    private OrderNoGenerator orderNoGenerator;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private OrderDelayService orderDelayService;

    OrderServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // [RabbitMQ] 集成后需加 @Transactional，配合发布者确认机制保证消息可靠性
    // [幂等性] 在 orderMapper.insert() 前加幂等校验，防止用户重复提交
    // 方案：Redis SETNX 检查 key: idempotent:order:{userId}:{idempotentToken}
    // 存在 → 已处理，直接返回；不存在 → 写入并继续
    // 兜底：order_info 表对 (user_id, restaurant_id, reservation_time) 加唯一索引
    @Override
    @Transactional
    public void createOrder(OrderDTO dto) {
        Restaurant restaurant = restaurantMapper.selectById(dto.getRestaurantId());
        if (restaurant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "餐厅不存在");
        }
        // 检查餐厅是否正常
        if (restaurant.getStatus() != Constants.RESTAURANT_STATUS_NORMAL) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "餐厅休息中");
        }
        if (!PhoneUtil.isMobile(dto.getContactPhone())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "联系电话格式错误");
        }

        OrderInfo orderInfo = new OrderInfo();
        // 用户上下文取用户ID
        Long userId = UserContext.getCurrentUserId();
        orderInfo.setUserId(userId); // ✅ 设置用户ID
        // 生成订单号
        String orderNo = orderNoGenerator.generateOrderNo();
        orderInfo.setOrderNo(orderNo);
        // 设置状态为待确认
        orderInfo.setStatus(OrderStatus.PENDING.getCode());

        BeanUtil.copyProperties(dto, orderInfo);
        orderMapper.insert(orderInfo); // ← 写库成功
        // ==================== 发送订单确认超时延迟消息 ====================
        eventPublisher.publishEvent(new OrderCreatedEvent(this, orderNo));
        log.info("订单创建成功，orderNo={}", orderNo);
    }

    @Override
    public Page<OrderVO> listOrders(Integer current, Integer pageSize, Integer status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        // 只查询当前用户自己的订单
        Long userId = UserContext.getCurrentUserId();
        wrapper.eq(OrderInfo::getUserId, userId);

        if (status != null) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        wrapper.eq(OrderInfo::getIsDeleted, 0).orderByDesc(OrderInfo::getUpdateTime);
        Page<OrderInfo> Firstpage = this.page(new Page<>(current, pageSize), wrapper);

        // 转换为VO列表VO
        List<OrderInfo> orderList = Firstpage.getRecords();
        // 空分页判断
        if (orderList.isEmpty()) {
            Page<OrderVO> emptyPage = new Page<>(Firstpage.getCurrent(), Firstpage.getSize(), 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }
        Set<Long> restaurantIds = orderList.stream()
                .map(OrderInfo::getRestaurantId)
                .collect(Collectors.toSet());
        // 批量查询餐厅信息
        List<Restaurant> restaurants = restaurantMapper.selectBatchIds(restaurantIds);
        // 构建 restaurantId → Restaurant 的 Map
        Map<Long, Restaurant> restaurantMap = restaurants.stream()
                .collect(Collectors.toMap(Restaurant::getId, r -> r));
        List<OrderVO> voList = orderList.stream()
                .map(order -> {
                    OrderVO vo = BeanUtil.copyProperties(order, OrderVO.class);
                    // 设置餐厅信息
                    Restaurant restaurant = restaurantMap.get(order.getRestaurantId());
                    if (restaurant != null) {
                        vo.setRestaurantId(restaurant.getId());
                        vo.setRestaurantName(restaurant.getName());
                    }
                    // 设置状态描述
                    vo.setStatus(order.getStatus());
                    vo.setStatusDesc(OrderStatus.getDescByCode(order.getStatus()));

                    return vo;
                })
                .collect(Collectors.toList());

        Page<OrderVO> resultPage = new Page<>(
                Firstpage.getCurrent(),
                Firstpage.getSize(),
                Firstpage.getTotal());
        resultPage.setRecords(voList);

        return resultPage;
    }

    @Override
    public OrderVO getOrderDetail(String orderNo) {
        Long userId = UserContext.getCurrentUserId();
        return cacheClient.queryWithPassThrough(
                Constants.REDIS_ORDER_DETAIL + userId + ":",
                orderNo,
                OrderVO.class,
                this::buildOrderVO,
                Constants.REDIS_EXPIRE_TIME,
                Constants.REDIS_EMPTY_KEY_EXPIRE_TIME,
                "order");
    }

    /**
     * 构造OrderVO
     * 
     * @param orderInfo
     * @return
     */
    private OrderVO buildOrderVO(String orderNo) {
        Long userId = UserContext.getCurrentUserId();
        // 查询数据库
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderMapper.selectOne(wrapper);
        if (orderInfo == null) {
            log.warn("订单不存在，orderNo={}", orderNo);
            return null;
        }
        if (!orderInfo.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "订单不属于当前用户，无权查看");
        }
        OrderVO orderVO = new OrderVO();
        BeanUtil.copyProperties(orderInfo, orderVO);
        orderVO.setStatusDesc(OrderStatus.getDescByCode(orderInfo.getStatus()));
        // 餐厅信息
        Restaurant restaurant = restaurantMapper.selectById(orderInfo.getRestaurantId());
        if (restaurant != null) {
            orderVO.setRestaurantId(restaurant.getId());
            orderVO.setRestaurantName(restaurant.getName());
        }
        return orderVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        Long userId = UserContext.getCurrentUserId();
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getIsDeleted, 0).eq(OrderInfo::getOrderNo, orderNo);
        // 根据订单号查询订单
        OrderInfo orderInfo = orderMapper.selectOne(wrapper);
        // 如果订单不存在，返回错误
        if (orderInfo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        // 校验是否为当前用户
        if (!userId.equals(orderInfo.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "订单不属于当前用户，无权取消");
        }
        // 校验订单状态
        if (!orderInfo.getStatus().equals(OrderStatus.PENDING.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单状态不支持取消");
        }
        // 更新订单状态
        orderInfo.setStatus(OrderStatus.CANCELLED.getCode());

        // 更新订单
        orderMapper.updateById(orderInfo);

        // 删除缓存
        cacheClient.delete(Constants.REDIS_ORDER_DETAIL + userId + ":" + orderNo);
        // TODO: [RabbitMQ] 取消成功后发送通知消息，告知用户订单已取消
        // 交换机: order.exchange，routing key: order.cancel
        // 消息体: { orderNo, userId }
    }

    @Override
    public Map<String, Integer> getUserPreferredCategories(Long userId) {
        List<Map<String, Object>> list = orderMapper.selectUserPreferredCategories(userId);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> map : list) {
            String category = (String) map.get("category");
            Integer count = (Integer) map.get("count");
            result.put(category, count);
        }
        return result;
    }

    @Override
    public void confirmOrder(String orderNo) {
        // 1.查询订单状态进行判空
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo, orderNo)
                .eq(OrderInfo::getIsDeleted, 0);
        OrderInfo orderInfo = orderMapper.selectOne(wrapper);
        if (orderInfo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!orderInfo.getStatus().equals(OrderStatus.PENDING.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单状态不支持确认");
        }
        orderInfo.setStatus(OrderStatus.CONFIRMED.getCode());
        orderMapper.updateById(orderInfo);
        // 确认后加入“到店超时30分钟自动取消”延时队列
        orderDelayService.enqueueNoShowCancel(orderInfo.getOrderNo(), orderInfo.getReservationTime());
        // 删除订单详情缓存（避免状态脏读）
        cacheClient.delete(Constants.REDIS_ORDER_DETAIL + orderInfo.getUserId() + ":" + orderInfo.getOrderNo());
    }

    @Override
    public void completeOrder(String orderNo) {
        // 1.查询订单状态进行判空
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getOrderNo, orderNo)
                .eq(OrderInfo::getIsDeleted, 0);
        OrderInfo orderInfo = orderMapper.selectOne(wrapper);
        if (orderInfo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!orderInfo.getStatus().equals(OrderStatus.CONFIRMED.getCode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前订单状态不支持确认完成订单");
        }
        // 2.更新状态更新数据库
        orderInfo.setStatus(OrderStatus.COMPLETED.getCode());
        orderMapper.updateById(orderInfo);
        // 3.删除订单详情缓存（状态更新了，就要删掉原缓存，不然会脏读）
        cacheClient.delete(Constants.REDIS_ORDER_DETAIL + orderInfo.getUserId() + ":" + orderInfo.getOrderNo());
    }
}
