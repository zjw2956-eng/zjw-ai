-- Active: 1773039044204@@127.0.0.1@3307@mysql
-- =============================================
-- 一键执行所有SQL脚本
-- 使用方式：mysql -uroot -p123456 -P3307 < all_in_one.sql
-- =============================================

-- 创建数据库
DROP DATABASE IF EXISTS `food_ai_system`;
CREATE DATABASE `food_ai_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `food_ai_system`;

-- =============================================
-- 核心表
-- =============================================

-- 1. 用户表
CREATE TABLE `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `phone` VARCHAR(11) UNIQUE COMMENT '手机号',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `avatar` VARCHAR(500) DEFAULT 'https://cdn.example.com/avatar/default.png' COMMENT '头像URL',
  `gender` TINYINT DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
  `birthday` DATE COMMENT '生日',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_phone (phone),
  INDEX idx_username (username),
  INDEX idx_status (status),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 餐厅表
CREATE TABLE `restaurant` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '餐厅ID',
  `name` VARCHAR(200) NOT NULL COMMENT '餐厅名称',
  `category` VARCHAR(50) COMMENT '菜系分类：川菜、粤菜、湘菜、日料、西餐等',
  `address` VARCHAR(500) COMMENT '详细地址',
  `latitude` DECIMAL(10,6) COMMENT '纬度',
  `longitude` DECIMAL(10,6) COMMENT '经度',
  `avg_price` DECIMAL(10,2) COMMENT '人均消费（元）',
  `rating` DECIMAL(3,2) DEFAULT 5.0 COMMENT '综合评分（1-5分）',
  `phone` VARCHAR(20) COMMENT '联系电话',
  `business_hours` VARCHAR(200) DEFAULT '10:00-22:00' COMMENT '营业时间',
  `images` TEXT COMMENT '图片URL列表（JSON数组）',
  `description` TEXT COMMENT '餐厅简介',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-停业，1-营业',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category),
  INDEX idx_rating (rating),
  INDEX idx_status (status),
  INDEX idx_avg_price (avg_price),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='餐厅表';

-- 3. 菜品表
CREATE TABLE `dish` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜品ID',
  `restaurant_id` BIGINT NOT NULL COMMENT '所属餐厅ID',
  `name` VARCHAR(100) NOT NULL COMMENT '菜品名称',
  `price` DECIMAL(10,2) NOT NULL COMMENT '价格（元）',
  `image` VARCHAR(500) COMMENT '菜品图片URL',
  `description` VARCHAR(500) COMMENT '菜品描述',
  `category` VARCHAR(50) COMMENT '菜品分类：主食、凉菜、热菜、汤类、甜品等',
  `is_recommend` TINYINT DEFAULT 0 COMMENT '是否推荐：0-否，1-是',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_restaurant (restaurant_id),
  INDEX idx_status (status),
  INDEX idx_recommend (is_recommend),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

-- 4. 订单表
CREATE TABLE `order_info` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `restaurant_id` BIGINT NOT NULL COMMENT '餐厅ID',
  `reservation_time` DATETIME NOT NULL COMMENT '预订时间',
  `people_count` INT NOT NULL COMMENT '用餐人数',
  `contact_name` VARCHAR(100) COMMENT '联系人',
  `contact_phone` VARCHAR(20) COMMENT '联系电话',
  `special_request` TEXT COMMENT '特殊要求',
  `status` TINYINT DEFAULT 0 COMMENT '状态：0-待确认，1-已确认，2-已完成，3-已取消',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_order_no (order_no),
  INDEX idx_user (user_id),
  INDEX idx_restaurant (restaurant_id),
  INDEX idx_status (status),
  INDEX idx_create_time (create_time),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 5. 评价表
CREATE TABLE `review` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '评价ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `restaurant_id` BIGINT NOT NULL COMMENT '餐厅ID',
  `order_id` BIGINT COMMENT '订单ID',
  `rating` TINYINT NOT NULL COMMENT '综合评分：1-5',
  `taste_rating` TINYINT COMMENT '口味评分：1-5',
  `environment_rating` TINYINT COMMENT '环境评分：1-5',
  `service_rating` TINYINT COMMENT '服务评分：1-5',
  `content` TEXT COMMENT '评价内容',
  `images` TEXT COMMENT '图片URL列表（JSON数组）',
  `status` TINYINT DEFAULT 0 COMMENT '审核状态：0-待审核，1-已通过，2-已拒绝',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user (user_id),
  INDEX idx_restaurant (restaurant_id),
  INDEX idx_order (order_id),
  INDEX idx_status (status),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- 6. 用户标签表
CREATE TABLE `user_tag` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标签ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `tag_type` VARCHAR(20) NOT NULL COMMENT '标签类型：taste-口味偏好，allergy-过敏源，diet-饮食习惯',
  `tag_value` VARCHAR(50) NOT NULL COMMENT '标签值：如"辣"、"甜"、"海鲜过敏"、"素食"等',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_tag (user_id, tag_type, tag_value),
  INDEX idx_user (user_id),
  INDEX idx_type (tag_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户标签表';

-- 7. 收藏表
CREATE TABLE `favorite` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `restaurant_id` BIGINT NOT NULL COMMENT '餐厅ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_restaurant (user_id, restaurant_id),
  INDEX idx_user (user_id),
  INDEX idx_restaurant (restaurant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- 8. 管理员表
CREATE TABLE `admin` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '管理员ID',
  `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
  `password` VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `phone` VARCHAR(11) COMMENT '手机号',
  `role` VARCHAR(20) DEFAULT 'admin' COMMENT '角色：super_admin-超级管理员，admin-普通管理员',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username),
  INDEX idx_status (status),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 9. AI对话记录表
CREATE TABLE `chat_log` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '对话ID',
  `memory_id` VARCHAR(50) NOT NULL COMMENT '会话ID',
  `user_id` BIGINT COMMENT '用户ID（可为空，未登录用户）',
  `user_message` TEXT NOT NULL COMMENT '用户消息',
  `ai_response` TEXT COMMENT 'AI回复',
  `token_count` INT COMMENT 'Token消耗数',
  `response_time` INT COMMENT '响应时间（毫秒）',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_memory_id (memory_id),
  INDEX idx_user (user_id),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话记录表';

-- 10. 知识库文档表
CREATE TABLE `knowledge_doc` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
  `title` VARCHAR(200) NOT NULL COMMENT '文档标题',
  `content` TEXT NOT NULL COMMENT '文档内容',
  `category` VARCHAR(50) COMMENT '文档分类：美食指南、健康饮食、菜谱等',
  `file_path` VARCHAR(500) COMMENT '文件路径',
  `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_category (category),
  INDEX idx_status (status),
  INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- =============================================
-- 插入测试数据
-- =============================================

-- 用户数据（密码：123456）
INSERT INTO `user` (`username`, `password`, `phone`, `nickname`, `gender`, `birthday`, `status`) VALUES
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138001', '张三', 1, '1995-06-15', 1),
('lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138002', '李四', 2, '1998-03-20', 1),
('wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138003', '王五', 1, '1992-11-08', 1),
('zhaoliu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138004', '赵六', 2, '2000-01-25', 1),
('sunqi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138005', '孙七', 1, '1996-09-12', 1);

-- 餐厅数据
INSERT INTO `restaurant` (`name`, `category`, `address`, `latitude`, `longitude`, `avg_price`, `rating`, `phone`, `business_hours`, `images`, `description`, `status`) VALUES
('蜀香阁川菜馆', '川菜', '成都市武侯区科华北路62号', 30.572269, 104.065735, 88.00, 4.8, '028-85223344', '11:00-14:00,17:00-22:00', '["https://cdn.example.com/restaurant/1/1.jpg","https://cdn.example.com/restaurant/1/2.jpg"]', '正宗川菜，麻辣鲜香，招牌菜有水煮鱼、毛血旺、宫保鸡丁', 1),
('粤味轩', '粤菜', '广州市天河区天河路208号', 23.135336, 113.323617, 158.00, 4.9, '020-38888888', '10:30-14:30,17:30-22:00', '["https://cdn.example.com/restaurant/2/1.jpg","https://cdn.example.com/restaurant/2/2.jpg"]', '精致粤菜，食材新鲜，招牌菜有白切鸡、烧鹅、虾饺', 1),
('湘味人家', '湘菜', '长沙市岳麓区麓山南路36号', 28.172567, 112.938814, 68.00, 4.6, '0731-88776655', '11:00-14:00,17:00-21:30', '["https://cdn.example.com/restaurant/3/1.jpg"]', '地道湘菜，香辣可口，招牌菜有剁椒鱼头、小炒肉、口味虾', 1),
('樱花日料', '日料', '上海市黄浦区南京东路300号', 31.235929, 121.481033, 198.00, 4.7, '021-63339999', '11:30-14:00,17:30-22:30', '["https://cdn.example.com/restaurant/4/1.jpg","https://cdn.example.com/restaurant/4/2.jpg"]', '新鲜刺身，精致寿司，环境优雅，适合商务宴请', 1),
('老北京炸酱面', '北京菜', '北京市东城区前门大街18号', 39.900625, 116.397026, 45.00, 4.5, '010-67052233', '10:00-21:00', '["https://cdn.example.com/restaurant/5/1.jpg"]', '老北京风味，炸酱面地道，还有卤煮、爆肚等小吃', 1),
('意式西餐厅', '西餐', '深圳市南山区科技园南区深南大道9988号', 22.537645, 113.944206, 228.00, 4.8, '0755-26881234', '11:00-15:00,17:30-23:00', '["https://cdn.example.com/restaurant/6/1.jpg","https://cdn.example.com/restaurant/6/2.jpg"]', '正宗意大利菜，牛排、意面、披萨，红酒品类丰富', 1),
('江南小厨', '江浙菜', '杭州市西湖区南山路102号', 30.242081, 120.148509, 118.00, 4.7, '0571-87065588', '11:00-14:00,17:00-21:00', '["https://cdn.example.com/restaurant/7/1.jpg"]', '江南风味，清淡鲜美，招牌菜有西湖醋鱼、东坡肉、龙井虾仁', 1),
('火锅英雄', '火锅', '重庆市渝中区解放碑步行街88号', 29.556742, 106.577145, 98.00, 4.9, '023-63829999', '11:00-凌晨02:00', '["https://cdn.example.com/restaurant/8/1.jpg","https://cdn.example.com/restaurant/8/2.jpg"]', '重庆老火锅，麻辣鲜香，锅底浓郁，毛肚、鸭肠必点', 1);

-- 菜品数据（部分示例）
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(1, '水煮鱼', 88.00, 'https://cdn.example.com/dish/1_1.jpg', '鲜嫩鱼片，麻辣鲜香，配菜丰富', '热菜', 1, 1),
(1, '毛血旺', 68.00, 'https://cdn.example.com/dish/1_2.jpg', '麻辣烫口，食材多样，下饭神器', '热菜', 1, 1),
(2, '白切鸡', 98.00, 'https://cdn.example.com/dish/2_1.jpg', '皮爽肉滑，原汁原味，配姜葱酱', '凉菜', 1, 1),
(2, '虾饺', 58.00, 'https://cdn.example.com/dish/2_3.jpg', '皮薄馅大，鲜虾饱满，晶莹剔透', '点心', 1, 1),
(8, '毛肚', 48.00, 'https://cdn.example.com/dish/8_1.jpg', '新鲜毛肚，七上八下，脆嫩爽口', '涮菜', 1, 1),
(8, '鸭肠', 38.00, 'https://cdn.example.com/dish/8_2.jpg', '鲜嫩脆爽，涮10秒即可，火锅必点', '涮菜', 1, 1);

-- 订单数据
INSERT INTO `order_info` (`order_no`, `user_id`, `restaurant_id`, `reservation_time`, `people_count`, `contact_name`, `contact_phone`, `special_request`, `status`) VALUES
('ORD202603080001', 1, 1, '2026-03-10 18:30:00', 4, '张三', '13800138001', '靠窗位置，不要太辣', 1),
('ORD202603080002', 2, 2, '2026-03-11 12:00:00', 2, '李四', '13800138002', '需要儿童座椅', 1),
('ORD202603080003', 3, 8, '2026-03-09 19:00:00', 6, '王五', '13800138003', '鸳鸯锅底', 2);

-- 评价数据
INSERT INTO `review` (`user_id`, `restaurant_id`, `order_id`, `rating`, `taste_rating`, `environment_rating`, `service_rating`, `content`, `status`) VALUES
(3, 8, 3, 5, 5, 5, 5, '火锅太好吃了！毛肚和鸭肠超级新鲜，锅底麻辣鲜香，服务员态度也很好，下次还会再来！', 1),
(2, 2, 2, 5, 5, 5, 5, '粤菜做得很正宗！白切鸡皮爽肉滑，虾饺晶莹剔透，环境也很好，强烈推荐！', 1);

-- 用户标签
INSERT INTO `user_tag` (`user_id`, `tag_type`, `tag_value`) VALUES
(1, 'taste', '辣'),
(1, 'diet', '无辣不欢'),
(2, 'taste', '清淡'),
(2, 'allergy', '海鲜过敏'),
(3, 'taste', '麻辣'),
(3, 'diet', '肉食主义');

-- 收藏数据
INSERT INTO `favorite` (`user_id`, `restaurant_id`) VALUES
(1, 1), (1, 8), (2, 2), (3, 8);

-- 管理员（密码：admin123）
INSERT INTO `admin` (`username`, `password`, `real_name`, `phone`, `role`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13900139000', 'super_admin', 1);

-- 知识库文档
INSERT INTO `knowledge_doc` (`title`, `content`, `category`, `status`) VALUES
('川菜特色与代表菜品', '川菜是中国八大菜系之一，以麻辣鲜香著称。代表菜品有：水煮鱼、毛血旺、宫保鸡丁、麻婆豆腐、回锅肉等...', '美食指南', 1),
('健康饮食建议', '均衡饮食应包含蛋白质、碳水化合物、脂肪、维生素和矿物质...', '健康饮食', 1);
