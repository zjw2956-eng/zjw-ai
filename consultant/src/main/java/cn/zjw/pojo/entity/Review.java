package cn.zjw.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评价实体
 */
@Data
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long restaurantId;
    private Long orderId;
    private Integer rating;
    private Integer tasteRating;
    private Integer environmentRating;
    private Integer serviceRating;
    private String content;
    private String images;
    private Integer status;

    //AI审核结果
    private String aiTags;

    private  String aiVerdict;   // APPROVE/REJECT/MANUAL_REVIEW


    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
