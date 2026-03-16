package cn.zjw.pojo.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class ReviewVO {
    private Long id;
    private Integer rating;
    private Integer tasteRating;
    private Integer environmentRating;
    private Integer serviceRating;
    private String content;
    private List<String> images;

    private Long userId;
    private String userNickname;
    private String userAvatar;

    private LocalDateTime createTime;
}
