package cn.zjw.pojo.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.io.Serializable;

import lombok.Data;


@Data
public class MyReviewVO implements Serializable{
    private Long id;
    private Integer rating;
    private Integer tasteRating;
    private Integer environmentRating;
    private Integer serviceRating;
    private String content;
    private List<String> images;

    private String orderNo;              // 关联订单号（便于用户对应）
    private Long restaurantId;
    private String restaurantName;       // 关联餐厅名
    private LocalDateTime createTime;

    private Integer status;              // 审核状态（用户自己能看到）
    private String statusDesc;           // "待审核" / "已通过" / "已拒绝"
}