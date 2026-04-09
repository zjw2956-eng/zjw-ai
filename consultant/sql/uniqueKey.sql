-- 用户表：单字段唯一索引
ALTER TABLE user ADD UNIQUE INDEX uk_username (username);
ALTER TABLE user ADD UNIQUE INDEX uk_phone (phone);

-- 评价表：联合唯一索引（同一用户对同一订单只能评价一次）
ALTER TABLE review ADD UNIQUE INDEX uk_user_order (user_id, order_id);