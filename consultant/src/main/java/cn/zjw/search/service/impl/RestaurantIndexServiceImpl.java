package cn.zjw.search.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;

import cn.zjw.mapper.RestaurantMapper;
import cn.zjw.pojo.entity.Restaurant;
import cn.zjw.search.document.RestaurantEsDoc;
import cn.zjw.search.repository.RestaurantSearchRepository;
import cn.zjw.search.service.RestaurantIndexService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RestaurantIndexServiceImpl implements RestaurantIndexService {

    @Autowired
    private RestaurantMapper restaurantMapper;
    @Autowired
    private RestaurantSearchRepository restaurantSearchRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void createIndexIfNeeded() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(RestaurantEsDoc.class);
        if (!indexOps.exists()) {
            boolean created = indexOps.create();
            if (!created) {
                throw new RuntimeException("创建 restaurant 索引失败");
            }
            boolean mappingCreated = indexOps.putMapping(indexOps.createMapping(RestaurantEsDoc.class));
            if (!mappingCreated) {
                throw new RuntimeException("创建 restaurant 索引 mapping 失败");
            }
            log.info("ES 索引创建成功: restaurant");
        } else {
            log.info("ES 索引已存在，跳过创建: restaurant");
        }

    }

    @Override
    public void importAllRestaurants() {
        List<Restaurant> restaurants = restaurantMapper.selectList(null);
        if (restaurants == null || restaurants.isEmpty()) {
            log.info("MySQL中无餐厅数据，跳过导入");
            return;
        }
        List<RestaurantEsDoc> docs = restaurants.stream()
                .map(this::toEsDoc)
                .collect(Collectors.toList());
        restaurantSearchRepository.saveAll(docs);
        log.info("全量导入餐厅数据到 ES 完成，数量: {}", docs.size());
    }

    private RestaurantEsDoc toEsDoc(Restaurant r) {
        RestaurantEsDoc doc = new RestaurantEsDoc();
        doc.setId(r.getId());
        doc.setName(r.getName());
        doc.setCategory(r.getCategory());
        doc.setAddress(r.getAddress());
        doc.setDescription(r.getDescription());
        doc.setAvgPrice(r.getAvgPrice());
        doc.setRating(r.getRating());
        doc.setStatus(r.getStatus());
        doc.setIsDeleted(r.getIsDeleted());
        return doc;
    }

    @Override
    public void syncRestaurantById(Long restaurantId) {
        // 判断餐厅Id是否为空
        if (restaurantId == null) {
            return;
        }
        // 查询餐厅，判断餐厅对象是否为空
        Restaurant restaurant = restaurantMapper.selectById(restaurantId);
        if (restaurant == null) {
            // 对象为空，内容被删了，从ES里面删文档
            restaurantSearchRepository.deleteById(restaurantId);
            log.info("餐厅不存在，已从 ES 删除文档: {}", restaurantId);
            return;
        }
        // 对象不空，添加文档
        restaurantSearchRepository.save(toEsDoc(restaurant));
        log.info("餐厅索引同步完成: {}", restaurantId);
    }

    @Override
    public void deleteRestaurantById(Long restaurantId) {
        if (restaurantId == null) {
            return;
        }
        restaurantSearchRepository.deleteById(restaurantId);
        log.info("已删除餐厅 ES 文档: {}", restaurantId);
    }

    @Override
    public void deleteIndexIfExists() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(RestaurantEsDoc.class);
        if (indexOps.exists()) {
            boolean deleted = indexOps.delete();
            if (!deleted) {
                throw new RuntimeException("删除 restaurant 索引失败");
            }
            log.info("删除 ES 索引成功: restaurant");
        } else {
            log.info("ES 索引不存在，跳过删除: restaurant");
        }
    }

    @Override
    public void rebuildIndex() {
        // 1. 先删旧索引
        deleteIndexIfExists();
        // 2. 重建索引和 mapping
        createIndexIfNeeded();
        // 3. 全量导入
        importAllRestaurants();
        log.info("重建 restaurant 索引完成");
    }
}
