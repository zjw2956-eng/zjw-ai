package cn.zjw.common.enums;

import lombok.Getter;

@Getter
public enum ReviewStatus {
    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝");

    private final Integer code;
    private final String desc;

    ReviewStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(Integer code) {
        if(code == null){
            return "未知状态";
        }
        for(ReviewStatus status:ReviewStatus.values()){
            if(status.getCode().equals(code)){
                return status.getDesc();
            }
        }
        return "未知状态";
    }
    
}
