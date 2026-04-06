package cn.zjw.search.document;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 餐厅 ES 文档
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "restaurant")
public class RestaurantEsDoc {

    @Id
    private Long id;

    /**
     * 餐厅名称
     */
    @Field(type = FieldType.Text)
    private String name;

    /**
     * 菜系分类，先作为精确筛选字段
     */
    @Field(type = FieldType.Keyword)
    private String category;

    /**
     * 餐厅地址
     */
    @Field(type = FieldType.Text)
    private String address;

    /**
     * 餐厅简介
     */
    @Field(type = FieldType.Text)
    private String description;

    /**
     * 人均消费
     */
    @Field(type = FieldType.Double)
    private BigDecimal avgPrice;

    /**
     * 综合评分
     */
    @Field(type = FieldType.Double)
    private BigDecimal rating;

    /**
     * 状态：0-停业，1-营业
     */
    @Field(type = FieldType.Integer)
    private Integer status;

    /**
     * 逻辑删除：0-未删除，1-已删除
     */
    @Field(type = FieldType.Integer)
    private Integer isDeleted;
}