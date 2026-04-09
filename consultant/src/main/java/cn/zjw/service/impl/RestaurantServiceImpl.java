package cn.zjw.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.bean.BeanUtil;
import cn.zjw.ai.model.RestaurantSummary;
import cn.zjw.ai.service.RestaurantSummaryService;
import cn.zjw.common.cache.CacheClient;
import cn.zjw.common.constant.Constants;
import cn.zjw.common.enums.DishStatus;
import cn.zjw.common.enums.ReviewStatus;
import cn.zjw.common.exception.BusinessException;
import cn.zjw.common.result.ResultCode;
import cn.zjw.mapper.DishMapper;
import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.mapper.ReviewMapper;
import cn.zjw.pojo.dto.RestaurantDTO;
import cn.zjw.pojo.entity.Dish;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.pojo.entity.Review;
import cn.zjw.pojo.vo.DishVO;
import cn.zjw.pojo.vo.RestaurantVO;
import cn.zjw.search.document.RestaurantEsDoc;
import cn.zjw.service.RestaurantService;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import lombok.extern.slf4j.Slf4j;

/**
 * 餐厅Service实现
 */
@Service
@Slf4j
public class RestaurantServiceImpl extends ServiceImpl<RestaurantMapper, Restaurant> implements RestaurantService {

    @Autowired
    private RestaurantMapper restaurantMapper;
    @Autowired
    private DishMapper dishMapper;

    @Lazy // 延迟加载，避免循环依赖
    @Autowired
    private RestaurantSummaryService restaurantSummaryService;

    @Autowired
    private ReviewMapper reviewMapper;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<RestaurantVO> listRestaurants(Integer current, Integer size,
            String category, BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating) {
        LambdaQueryWrapper<Restaurant> wrapper = new LambdaQueryWrapper<Restaurant>()
                /**
                 * 分页条件
                 * 状态正常：营业中
                 * 未删除：正常营业
                 * 分类匹配：可选菜系
                 * 价格范围：可选价格区间
                 * 评分范围：可选评分区间
                 * 排序：按评分降序
                 */
                .eq(Restaurant::getStatus, Constants.RESTAURANT_STATUS_NORMAL)
                .eq(Restaurant::getIsDeleted, 0)
                .eq(StringUtils.hasText(category), Restaurant::getCategory, category)
                // 价格范围 ge是大于等于，le是小于等于
                .ge(minPrice != null, Restaurant::getAvgPrice, minPrice)
                .le(maxPrice != null, Restaurant::getAvgPrice, maxPrice)
                .ge(minRating != null, Restaurant::getRating, minRating)
                .orderByDesc(Restaurant::getRating);
        // 关键步骤，执行分页查询，MyBatis-Plus提供的page方法
        // 返回的 restaurantPage 里装好了：当前页数据列表 + 总记录数 + 总页数
        Page<Restaurant> restaurantPage = this.page(new Page<>(current, size), wrapper);

        /**
         * getRecords() → 拿出当前页的餐厅列表（List<Restaurant>）
         * BeanUtil.copyProperties → 把每个 Restaurant（数据库实体）复制成 RestaurantVO（返回给前端的对象）
         * collect(Collectors.toList()) → 把所有复制好的 RestaurantVO 收集成一个 List
         */
        List<RestaurantVO> voList = restaurantPage.getRecords().stream()
                .map(r -> BeanUtil.copyProperties(r, RestaurantVO.class))
                .collect(Collectors.toList());

        /**
         * 为什么不直接返回 restaurantPage？
         * 因为它是 Page<Restaurant> 类型，里面装的是实体，不是 VO。
         * 所以需要新建一个 Page<RestaurantVO>，然后把总数/页码这些信息从旧的搬过来。
         */
        Page<RestaurantVO> resultPage = new Page<>(
                restaurantPage.getCurrent(),
                restaurantPage.getSize(),
                restaurantPage.getTotal());
        resultPage.setRecords(voList);

        return resultPage;
    }

    @Override
    public RestaurantVO getRestaurantById(Long id) {
        RestaurantVO result = cacheClient.queryWithMutex(
                Constants.REDIS_RESTAURANT_KEY,
                Constants.REDIS_LOCK_RESTAURANT_KEY,
                id,
                RestaurantVO.class,
                this::buildRestaurantVO,
                Constants.REDIS_EXPIRE_TIME,
                Constants.REDIS_EMPTY_KEY_EXPIRE_TIME,
                "restaurant");
        if (result == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "餐厅不存在");
        }
        return result;
    }

    /**
     * 构造RestaurantVO
     * 
     * @param restaurantId
     * @return
     */
    private RestaurantVO buildRestaurantVO(Long id) {
        // 缓存未命中，查数据库
        Restaurant restaurant = restaurantMapper.selectById(id);
        if (restaurant == null) {
            return null;
        }
        // 查询菜品
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(Dish::getRestaurantId, id)
                .eq(Dish::getStatus, DishStatus.ON_SALE.getCode())
                .eq(Dish::getIsDeleted, 0)
                .orderByDesc(Dish::getIsRecommend);
        List<Dish> dishList = dishMapper.selectList(wrapper);

        List<DishVO> dishVOList = dishList.stream().map(d -> BeanUtil.copyProperties(d, DishVO.class))
                .collect(Collectors.toList());

        // AI生成餐厅评价摘要（仅返回摘要文本）
        String aiSummary = getSummary(id);
        // Entity → VO，写入缓存
        RestaurantVO restaurantVO = BeanUtil.copyProperties(restaurant, RestaurantVO.class);
        // 设置菜品列表
        restaurantVO.setDishes(dishVOList);
        // 设置评价摘要
        restaurantVO.setAiSummary(aiSummary);
        // TODO: [缓存一致性] 当菜品状态变更（上架/下架/新增/删除）时，需删除餐厅缓存
        // 涉及接口：DishService.addDish()、DishService.updateDishStatus() 等
        // 删除方式：stringRedisTemplate.delete(Constants.REDIS_RESTAURANT_KEY +
        // restaurantId)
        return restaurantVO;
    }

    /**
     * 获取或生成餐厅摘要（带缓存）
     */
    private String getSummary(Long restaurantId) {
        return cacheClient.queryWithMutex(
                Constants.REDIS_RESTAURANT_SUMMARY_KEY,
                Constants.REDIS_LOCK_RESTAURANT_SUMMARY_KEY,
                restaurantId,
                String.class,
                this::buildSummaryFromDbAndAi,
                Constants.REDIS_RESTAURANT_SUMMARY_EXPIRE_TIME,
                Constants.REDIS_EMPTY_KEY_EXPIRE_TIME,
                "restaurant_summary");
    }

    /**
     * 从数据库和AI生成摘要
     * 
     * @param restaurantId
     * @return
     */
    private String buildSummaryFromDbAndAi(Long restaurantId) {
        // 缓存不存在，查询最近 20 条已通过审核的评价
        LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.eq(Review::getRestaurantId, restaurantId)
                .eq(Review::getIsDeleted, 0)
                .eq(Review::getStatus, ReviewStatus.APPROVED.getCode())
                .orderByDesc(Review::getCreateTime)
                .last("limit 20");
        List<Review> reviews = reviewMapper.selectList(reviewWrapper);
        if (reviews.size() < 3) {
            return null;
        }
        // 把List<Review>转换为String
        String reviewsText = reviews.stream()
                .map(r -> "评分:" + r.getRating() + "星，内容：" + r.getContent())
                .collect(Collectors.joining("\n"));
        try {
            log.info("准备调用 AI 生成摘要，评价文本: {}", reviewsText);
            // 调用AI服务生成餐厅摘要，返回结构化对象RestaurantSummary
            RestaurantSummary restaurantSummary = restaurantSummaryService.generateSummary(reviewsText);
            log.info("AI 返回的摘要对象: {}", restaurantSummary);
            String summaryText = restaurantSummary == null ? null : restaurantSummary.summary();
            if (!StringUtils.hasText(summaryText)) {
                return null;
            }
            return summaryText;
        } catch (Exception e) {
            log.error("AI 生成摘要失败: restaurantId={}", restaurantId, e);
            return null;
        }
    }

    @Override
    public void addRestaurant(RestaurantDTO dto) {
        // TODO: 新增餐厅（需要管理员/商家权限）
        // 1. 权限校验（管理员或商家角色才能调用）
        // 2. 校验餐厅名称是否重复
        // 3. DTO → Entity
        // 4. 保存到数据库
    }

    @Override
    public void updateRestaurant(RestaurantDTO dto) {
        // TODO: 修改餐厅（需要管理员/商家权限）
        // 1. 权限校验（只有该餐厅的商家或管理员才能修改）
        // 2. 校验 dto.getId() 不为空
        // 3. 校验餐厅是否存在
        // 4. DTO → Entity，更新到数据库
        // 5. 删除该餐厅的 Redis 缓存，防止读到旧数据
    }

    @Override
    public List<Restaurant> getTopRatedRestaurants(String category, BigDecimal minRating, Integer limit) {
        // 查询高分餐厅（用于推荐）
        LambdaQueryWrapper<Restaurant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Restaurant::getIsDeleted, 0)
                .eq(category != null, Restaurant::getCategory, category)
                .ge(minRating != null, Restaurant::getRating, minRating)
                .orderByDesc(Restaurant::getRating)
                .last("LIMIT " + (limit == null ? 10 : limit));
        return restaurantMapper.selectList(wrapper);
    }

    @Override
    public Page<RestaurantVO> searchRestaurants(Integer current, Integer size, String keyword, String category,
            BigDecimal minPrice, BigDecimal maxPrice, BigDecimal minRating) {
        // 1.构造bool查询
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        // 关键词（name/address/description）
        if (StringUtils.hasText(keyword)) {
            Query keywordQuery = Query.of(q -> q.multiMatch(m -> m
                    .query(keyword)
                    .fields("name", "address", "description", "category.text")
                    // 关键：开启模糊匹配（编辑距离容错）
                    .fuzziness("AUTO")
                    // 可选：至少前 N 个字符必须精确，避免太“飘”
                    .prefixLength(1)
                    // 可选：控制模糊展开数量，避免性能抖动
                    .maxExpansions(50)));
            boolBuilder.must(keywordQuery);
        }
        // 固定过滤：营业中+未删除
        boolBuilder.filter(Query.of(q -> q.term(t -> t.field("status").value(Constants.RESTAURANT_STATUS_NORMAL))));
        boolBuilder.filter(Query.of(q -> q.term(t -> t.field("isDeleted").value(0))));
        // 可选过滤：分类、价格区间、评分
        if (StringUtils.hasText(category)) {
            boolBuilder.filter(Query.of(q -> q.term(t -> t
                    .field("category")
                    .value(category))));
        }
        if (minPrice != null) {
            boolBuilder.filter(Query.of(q -> q.range(r -> r
                    .field("avgPrice")
                    .gte(JsonData.of(minPrice.doubleValue())))));
        }

        if (maxPrice != null) {
            boolBuilder.filter(Query.of(q -> q.range(r -> r
                    .field("avgPrice")
                    .lte(JsonData.of(maxPrice.doubleValue())))));
        }

        if (minRating != null) {
            boolBuilder.filter(Query.of(q -> q.range(r -> r
                    .field("rating")
                    .gte(JsonData.of(minRating.doubleValue())))));
        }
        // 分页参数
        int pageNo = (current == null || current < 1) ? 1 : current;
        int pageSize = (size == null || size < 1) ? 10 : size;

        // 排序。先相关度，再评分；配置高亮
        HighlightParameters highlightParameters = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .build();

        Highlight highlight = new Highlight(
                highlightParameters,
                List.of(
                        new HighlightField("name"),
                        new HighlightField("description")));

        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q.bool(boolBuilder.build())))
                .withSort(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))))
                .withSort(SortOptions.of(s -> s.field(f -> f.field("rating").order(SortOrder.Desc))))
                .withPageable(PageRequest.of(pageNo - 1, pageSize))
                .withHighlightQuery(new HighlightQuery(highlight, RestaurantEsDoc.class))
                .build();
        // 执行查询
        SearchHits<RestaurantEsDoc> hits = elasticsearchOperations.search(query, RestaurantEsDoc.class);

        // 转VO，并应用高亮结果
        List<RestaurantVO> voList = hits.getSearchHits().stream()
                .map(hit -> {
                    RestaurantEsDoc doc = hit.getContent();
                    RestaurantVO vo = new RestaurantVO();
                    vo.setId(doc.getId());
                    vo.setName(doc.getName());
                    vo.setCategory(doc.getCategory());
                    vo.setAddress(doc.getAddress());
                    vo.setDescription(doc.getDescription());
                    vo.setAvgPrice(doc.getAvgPrice());
                    vo.setRating(doc.getRating());
                    vo.setStatus(doc.getStatus());

                    // 应用高亮字段（若存在）
                    if (hit.getHighlightFields() != null && !hit.getHighlightFields().isEmpty()) {
                        List<String> nameHighlights = hit.getHighlightFields().get("name");
                        if (nameHighlights != null && !nameHighlights.isEmpty()) {
                            vo.setName(nameHighlights.get(0));
                        }

                        List<String> descHighlights = hit.getHighlightFields().get("description");
                        if (descHighlights != null && !descHighlights.isEmpty()) {
                            vo.setDescription(descHighlights.get(0));
                        }
                    }

                    return vo;
                })
                .collect(Collectors.toList());
        // 组装MyBatis-Plus Page 返回给前端（保持接口不变）
        Page<RestaurantVO> resultPage = new Page<>(pageNo, pageSize, hits.getTotalHits());
        resultPage.setRecords(voList);
        return resultPage;
    }
}
