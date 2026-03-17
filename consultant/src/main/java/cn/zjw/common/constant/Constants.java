package cn.zjw.common.constant;

public class Constants {

    //Redis键值对
    //评价详情缓存键
    public static final String REDIS_REVIEW_DETAIL = "review:detail:";
    //订单详情缓存键
    public static final String REDIS_ORDER_DETAIL = "order:detail:";
    //用户token缓存键
    public static final String REDIS_USER_TOKEN = "user:token:";
    public static final String REDIS_RESTAURANT_KEY = "restaurant:info:";
    //防止缓存穿透的空值的过期时间,2分钟
    public static final Long REDIS_EMPTY_KEY_EXPIRE_TIME = 2 * 60L;
    //缓存过期时间，72小时
    public static final Long REDIS_EXPIRE_TIME = 72 * 60 * 60L;

    //餐厅状态
    public static final Integer RESTAURANT_STATUS_NORMAL = 1;
    public static final Integer RESTAURANT_STATUS_DISABLED = 0;

    //订单号前缀
    public static final String ORDER_ID_PREFIX = "ORDER";


}
