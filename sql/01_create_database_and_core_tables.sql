-- =============================================
-- 智能美食推荐与餐厅管理系统
-- 数据库名：food_ai_system
-- =============================================

-- 创建数据库
DROP DATABASE IF EXISTS `food_ai_system`;
CREATE DATABASE `food_ai_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `food_ai_system`;

-- =============================================
-- 1. 用户表
-- =============================================
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

-- =============================================
-- 2. 餐厅表
-- =============================================
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

-- =============================================
-- 3. 菜品表
-- =============================================
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

-- =============================================
-- 4. 订单表（order是保留字，使用order_info）
-- =============================================
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
