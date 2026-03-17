package cn.zjw.common.enums;

import lombok.Getter;

/**
 * 菜品状态枚举
 */
@Getter
public enum DishStatus {
    OFF_SALE(0, "下架"),
    ON_SALE(1, "上架");

    private final Integer code;
    private final String desc;

    DishStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取中文描述
     * @param code 状态码 0/1
     * @return 中文描述
     */
    public static String getDescByCode(Integer code){
        if(code == null){
            return "未知菜品状态";
        }
        for(DishStatus status : DishStatus.values()){
            if(status.getCode().equals(code)){
                return status.getDesc();
            }
        }
        return "未知菜品状态";
    }
}
