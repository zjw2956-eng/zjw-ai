-- =============================================
-- 其他业务表
-- =============================================

USE `food_ai_system`;

-- =============================================
-- 5. 评价表
-- =============================================
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

-- =============================================
-- 6. 用户标签表（饮食偏好）
-- =============================================
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

-- =============================================
-- 7. 收藏表
-- =============================================
CREATE TABLE `favorite` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `restaurant_id` BIGINT NOT NULL COMMENT '餐厅ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_restaurant (user_id, restaurant_id),
  INDEX idx_user (user_id),
  INDEX idx_restaurant (restaurant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- =============================================
-- 8. 管理员表
-- =============================================
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

-- =============================================
-- 9. AI对话记录表
-- =============================================
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

-- =============================================
-- 10. 知识库文档表（RAG管理）
-- =============================================
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
