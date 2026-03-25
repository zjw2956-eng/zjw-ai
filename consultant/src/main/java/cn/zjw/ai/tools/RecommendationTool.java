package cn.zjw.ai.tools;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import cn.hutool.json.JSONUtil;
import cn.zjw.common.context.UserContext;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.vo.MyReviewVO;
import cn.zjw.service.OrderService;
import cn.zjw.service.RestaurantService;
import cn.zjw.service.ReviewService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RecommendationTool {
    @Autowired
    private OrderService orderService;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private ReviewService reviewService;

    /**
     * 查询当前用户订单历史，分析用户常点的菜系统计
     */
    @Tool("""
                Query current user's order history to analyze preferred cuisines.

                Call this method when:
                - User asks for restaurant recommendations
                - Need to understand user's dining preferences
                - Analyzing user's favorite cuisine types

                Returns: JSON string containing cuisine statistics,
                         format: {"川菜": 5, "粤菜": 3, "日料": 2}

                Use this data to provide personalized recommendations.
            """)
    public String getUserOrderHistory() {
        try {
            Long userId = UserContext.getCurrentUserId();
            log.info("查询用户订单历史，用户ID：{}", userId);
            Map<String, Integer> preferredCategories = orderService.getUserPreferredCategories(userId);
            log.info("用户常点的菜系统计：{}", preferredCategories);
            return JSONUtil.toJsonStr(preferredCategories);
        } catch (Exception e) {
            log.error("查询用户订单历史失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询高分餐厅列表，根据菜系、最低评分、数量筛选
     */
    @Tool("""
                    Query top-rated restaurants with optional filters.

                    Call this method when:
                    - User asks for highly-rated restaurants
                    - Need to recommend restaurants by cuisine type
                    - User specifies minimum rating requirement

                    Parameters:
                    - category: Cuisine type (e.g., "川菜", "粤菜", "日料"),pass null for all
                    - minRating: Minimum rating (e.g., 4.5), pass null for no limit
                    - limit: Maximum number of results (recommended: 5-10)

                    Returns: JSON array of restaurant objects with name,rating, category, etc.
                """)
    public String getTopRatedRestaurants(
            @P("Cuisine category, null for all") String category,
            @P("Minimum rating, null for no limit") BigDecimal minRating,
            @P("Maximum number of results") Integer limit) {
        try {
            log.info("查询高分餐厅列表，菜系：{}，最低评分：{}，数量：{}",
                    category, minRating, limit);
            List<Restaurant> topRestaurants = restaurantService.getTopRatedRestaurants(category, minRating,
                    limit);
            log.info("查询到 {} 家高分餐厅",
                    topRestaurants.size());
            return JSONUtil.toJsonStr(topRestaurants);
        } catch (Exception e) {
            log.error("查询高分餐厅失败", e);
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询当前用户评价历史，分析用户评价偏好
     */
    @Tool("""
                    Query current user's review history to understand preferences.

                    Call this method when:
                    - User asks for personalized recommendations
                    - Need to analyze user's taste preferences
                    - Understanding which restaurants user liked/disliked

                    Parameters:
                    - limit: Maximum number of reviews to return (recommended:5-10)

                    Returns: JSON array of review objects containing:
                             - restaurantName: Name of the restaurant
                             - rating: Overall rating (1-5)
                             - content: Review text
                             - createTime: When the review was posted

                    Use this data to identify user's preferences and provide
            better recommendations.
                """)
    public String getUserReviews(@P("Maximum number of reviews") Integer limit) {
        try {
            Long userId = UserContext.getCurrentUserId();
            log.info("查询用户评价历史，用户ID：{}，数量：{}", userId, limit);
            List<MyReviewVO> list = reviewService.getUserReviewHistory(userId, limit);
            log.info("查询到 {} 条用户评价", list.size());
            return JSONUtil.toJsonStr(list);
        } catch (Exception e) {
            log.error("查询用户评价历史失败", e);
            return "查询失败：" + e.getMessage();
        }
    }
}
