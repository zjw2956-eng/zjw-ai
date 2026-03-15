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

    /**
     * 根据状态码获取中文描述
     * @param code 状态码 0/1/2/3
     * @return 中文描述
     */
    public static String getDescByCode(Integer code){
        if(code == null){
            return "未知状态";
        }
        for(OrderStatus status : OrderStatus.values()){
            if(status.getCode().equals(code)){
                return status.getDesc();
            }
        }
        return "未知状态";
    }
}
