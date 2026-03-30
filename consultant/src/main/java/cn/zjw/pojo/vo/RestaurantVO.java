package cn.zjw.pojo.vo;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RestaurantVO {
    private Long id;
    private String name;
    private String category;
    private String address;
    private BigDecimal avgPrice;
    private BigDecimal rating;
    private String phone;
    private String businessHours;
    private String images;
    private String description;
    private Integer status;
    private List<DishVO> dishes; // 菜品列表
    
    private String aiSummary;  // AI 生成的口碑摘要（JSON 字符串）
}
