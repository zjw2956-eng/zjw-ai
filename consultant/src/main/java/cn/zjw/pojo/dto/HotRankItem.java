package cn.zjw.pojo.dto;

import lombok.Data;


@Data
public class HotRankItem {
    private Long restaurantId;
    private Long score; // 或 Double
}