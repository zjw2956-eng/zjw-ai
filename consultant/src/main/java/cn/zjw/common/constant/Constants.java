package cn.zjw.common.constant;

public class Constants {

    //Redis键值对常量
    //消息队列生产者确认机制常量
    public static final String RABBITMQ_CORRELATION_MSG_ID = "mq:msgId:";
    //最大重试次数
    public static final Integer MAX_RETRY_COUNT = 3;
    //重试时间间隔，单位秒
    public static final Long MQ_RETRY_INTERVAL_TIME = 5L * 60L;
    //重试次数大于3次的失败消息缓存
    public static final String MQ_FAILED_RETRY_KEY = "mq:retry:failed:msg:";
    //重试次数大于3次的失败消息缓存过期时间，72小时
    public static final Long MQ_FAILED_RETRY_EXPIRE_TIME = 72 * 60 * 60L;

    //餐厅摘要缓存键
    public static final String REDIS_RESTAURANT_SUMMARY_KEY = "restaurant:ai:summary:";
    //餐厅摘要缓存过期时间，24小时
    public static final Long REDIS_RESTAURANT_SUMMARY_EXPIRE_TIME = 24 * 60 * 60L;
    //评价详情缓存键
    public static final String REDIS_REVIEW_DETAIL = "review:detail:";
    //订单详情缓存键
    public static final String REDIS_ORDER_DETAIL = "order:detail:";
    //用户token缓存键
    public static final String REDIS_USER_TOKEN = "user:token:";
    //餐厅信息缓存键
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

    //Redission锁键值对常量
    //订单号生成器
    public static final String REDIS_ORDER_NO_GENERATOR = "order:generate:lock";
    //订单号序列
    public static final String REDIS_ORDER_NO_SEQ = "order:seq:";


}
