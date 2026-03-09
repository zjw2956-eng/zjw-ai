package cn.zjw.common.constant;

/**
 * 常量类
 */
public class Constants {
    // JWT相关
    public static final String JWT_SECRET = "food_ai_system_secret_key";
    public static final Long JWT_EXPIRE = 7 * 24 * 60 * 60 * 1000L; // 7天

    // Redis Key前缀
    public static final String REDIS_USER_TOKEN = "user:token:";
    public static final String REDIS_RESTAURANT_CACHE = "restaurant:";

    // 默认分页参数
    public static final Integer DEFAULT_PAGE_SIZE = 10;
    public static final Integer DEFAULT_PAGE_NUM = 1;
}
