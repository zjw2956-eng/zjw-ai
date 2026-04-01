package cn.zjw.mq.message;

import java.io.Serializable;

import lombok.Data;

@Data
public class ReviewAuditMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long reviewId; // 评价ID
    private String content; // 评价内容
    private Integer rating; // 评价等级（1-5星）
}
