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

    /** 关键词搜索时回填，便于前端高亮 */
    private String keywords;
    /** 该餐厅中匹配关键词的菜品ID列表，仅关键词搜索时填充 */
    private List<Long> matchedDishIds;
}
