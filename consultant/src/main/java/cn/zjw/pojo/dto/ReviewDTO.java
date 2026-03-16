package cn.zjw.pojo.dto;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class ReviewDTO implements Serializable{

    @NotNull(message="订单ID不能为空")
    private Long orderId;

    @NotNull(message="综合评分不能为空")
    @Min(value=1,message="综合评分最低1分")
    @Max(value=5,message="综合评分最高5分")
    private Integer rating;

    @Min(1) @Max(5)
    private Integer tasteRating;          // 口味评分（选填）
    @Min(1) @Max(5)
    private Integer environmentRating;   // 环境评分（选填）
    @Min(1) @Max(5)
    private Integer serviceRating;       // 服务评分（选填）
    @Size(max = 500, message = "评价内容不超过500字")
    private String content;              // 文字评价（选填）
    private List<String> images;         // 图片URL列表（选填，存库前序列化为JSON）
    
}
