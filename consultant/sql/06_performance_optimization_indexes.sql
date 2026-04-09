-- =============================================
-- 性能优化：添加索引
-- 执行时间：2026-04-08
-- =============================================

USE `food_ai_system`;

-- =============================================
-- 1. 订单查询优化：添加联合索引
-- =============================================
-- 场景：用户查询自己的订单列表（按更新时间倒序）
-- SQL: SELECT * FROM order_info WHERE user_id = ? AND status = ? AND is_deleted = 0 ORDER BY update_time DESC
-- 优化：添加覆盖索引（user_id, is_deleted, status, update_time）
ALTER TABLE `order_info`
ADD INDEX idx_user_deleted_status_update (user_id, is_deleted, status, update_time);

-- 说明：
-- 1. user_id：等值查询，放最前面（最左前缀原则）
-- 2. is_deleted：等值查询，过滤逻辑删除数据
-- 3. status：可能为 NULL（查询全部状态），相当于范围查询
-- 4. update_time：排序字段，放最后

-- =============================================
-- 2. 评价查询优化：添加联合索引
-- =============================================
-- 场景：查询餐厅的已审核评价列表（按创建时间倒序）
-- SQL: SELECT * FROM review WHERE restaurant_id = ? AND status = 'APPROVED' ORDER BY create_time DESC
-- 优化：添加覆盖索引（restaurant_id, status, create_time）
ALTER TABLE `review`
ADD INDEX idx_restaurant_status_create (restaurant_id, status, create_time);

-- 说明：
-- 1. restaurant_id：等值查询，放最前面
-- 2. status：等值查询（只查已审核的）
-- 3. create_time：排序字段，放最后

-- =============================================
-- 3. 热门榜单统计优化：添加联合索引
-- =============================================
-- 场景：统计近30天各餐厅的订单数（用于热门榜单）
-- SQL: SELECT restaurant_id, COUNT(*) FROM order_info
--      WHERE status = 2 AND reservation_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
--      GROUP BY restaurant_id
-- 优化：添加覆盖索引（status, reservation_time, restaurant_id）
ALTER TABLE `order_info`
ADD INDEX idx_status_time_restaurant (status, reservation_time, restaurant_id);

-- 说明：
-- 1. status：等值查询（只统计已完成订单）
-- 2. reservation_time：范围查询（近30天）
-- 3. restaurant_id：分组字段

-- =============================================
-- 4. 用户评价查询优化：添加联合索引
-- =============================================
-- 场景：查询用户的评价历史（用于个性化推荐）
-- SQL: SELECT * FROM review WHERE user_id = ? ORDER BY create_time DESC
-- 优化：添加覆盖索引（user_id, create_time）
ALTER TABLE `review`
ADD INDEX idx_user_create (user_id, create_time);

-- =============================================
-- 验证索引效果
-- =============================================
-- 1. 查看表的所有索引
SHOW INDEX FROM order_info;
SHOW INDEX FROM review;

-- 2. 分析查询计划（优化前后对比）
EXPLAIN SELECT * FROM order_info
WHERE user_id = 1 AND status = 1 AND is_deleted = 0
ORDER BY update_time DESC
LIMIT 10;

EXPLAIN SELECT * FROM review
WHERE restaurant_id = 1 AND status = 'APPROVED'
ORDER BY create_time DESC
LIMIT 10;

-- 3. 查看索引使用情况
SELECT
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    CARDINALITY
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'food_ai_system'
  AND TABLE_NAME IN ('order_info', 'review')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
