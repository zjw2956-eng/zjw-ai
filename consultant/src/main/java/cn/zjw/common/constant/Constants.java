package cn.zjw.common.constant;

public class Constants {

    public static final String REDIS_USER_TOKEN = "user:token:";
    public static final String REDIS_RESTAURANT_KEY = "restaurant:info:";
    public static final Long REDIS_EXPIRE_TIME = 72 * 60 * 60L;

    public static final Integer RESTAURANT_STATUS_NORMAL = 1;
    public static final Integer RESTAURANT_STATUS_DISABLED = 0;
}
