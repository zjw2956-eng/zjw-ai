package cn.zjw.common.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatus {
    PENDING(0, "待确认"),
    CONFIRMED(1, "已确认"),
    COMPLETED(2, "已完成"),
    CANCELLED(3, "已取消");

    private final Integer code;
    private final String desc;

    OrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
