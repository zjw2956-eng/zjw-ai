package cn.zjw.search.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import cn.zjw.search.document.RestaurantEsDoc;

/**
 * 餐厅搜索仓库
 * 先用基础 CRUD 能力（save/saveAll/deleteById）
 * 复杂检索后续用 ElasticsearchRestTemplate 实现
 */
public interface RestaurantSearchRepository extends ElasticsearchRepository<RestaurantEsDoc, Long> {
}
