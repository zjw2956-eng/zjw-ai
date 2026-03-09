-- =============================================
-- 测试数据 - 用户和餐厅
-- =============================================

USE `food_ai_system`;

-- =============================================
-- 插入测试用户（密码统一为：123456，BCrypt加密后）
-- =============================================
INSERT INTO `user` (`username`, `password`, `phone`, `nickname`, `gender`, `birthday`, `status`) VALUES
('zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138001', '张三', 1, '1995-06-15', 1),
('lisi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138002', '李四', 2, '1998-03-20', 1),
('wangwu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138003', '王五', 1, '1992-11-08', 1),
('zhaoliu', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138004', '赵六', 2, '2000-01-25', 1),
('sunqi', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '13800138005', '孙七', 1, '1996-09-12', 1);

-- =============================================
-- 插入测试餐厅
-- =============================================
INSERT INTO `restaurant` (`name`, `category`, `address`, `latitude`, `longitude`, `avg_price`, `rating`, `phone`, `business_hours`, `images`, `description`, `status`) VALUES
('蜀香阁川菜馆', '川菜', '成都市武侯区科华北路62号', 30.572269, 104.065735, 88.00, 4.8, '028-85223344', '11:00-14:00,17:00-22:00', '["https://cdn.example.com/restaurant/1/1.jpg","https://cdn.example.com/restaurant/1/2.jpg"]', '正宗川菜，麻辣鲜香，招牌菜有水煮鱼、毛血旺、宫保鸡丁', 1),
('粤味轩', '粤菜', '广州市天河区天河路208号', 23.135336, 113.323617, 158.00, 4.9, '020-38888888', '10:30-14:30,17:30-22:00', '["https://cdn.example.com/restaurant/2/1.jpg","https://cdn.example.com/restaurant/2/2.jpg"]', '精致粤菜，食材新鲜，招牌菜有白切鸡、烧鹅、虾饺', 1),
('湘味人家', '湘菜', '长沙市岳麓区麓山南路36号', 28.172567, 112.938814, 68.00, 4.6, '0731-88776655', '11:00-14:00,17:00-21:30', '["https://cdn.example.com/restaurant/3/1.jpg"]', '地道湘菜，香辣可口，招牌菜有剁椒鱼头、小炒肉、口味虾', 1),
('樱花日料', '日料', '上海市黄浦区南京东路300号', 31.235929, 121.481033, 198.00, 4.7, '021-63339999', '11:30-14:00,17:30-22:30', '["https://cdn.example.com/restaurant/4/1.jpg","https://cdn.example.com/restaurant/4/2.jpg"]', '新鲜刺身，精致寿司，环境优雅，适合商务宴请', 1),
('老北京炸酱面', '北京菜', '北京市东城区前门大街18号', 39.900625, 116.397026, 45.00, 4.5, '010-67052233', '10:00-21:00', '["https://cdn.example.com/restaurant/5/1.jpg"]', '老北京风味，炸酱面地道，还有卤煮、爆肚等小吃', 1),
('意式西餐厅', '西餐', '深圳市南山区科技园南区深南大道9988号', 22.537645, 113.944206, 228.00, 4.8, '0755-26881234', '11:00-15:00,17:30-23:00', '["https://cdn.example.com/restaurant/6/1.jpg","https://cdn.example.com/restaurant/6/2.jpg"]', '正宗意大利菜，牛排、意面、披萨，红酒品类丰富', 1),
('江南小厨', '江浙菜', '杭州市西湖区南山路102号', 30.242081, 120.148509, 118.00, 4.7, '0571-87065588', '11:00-14:00,17:00-21:00', '["https://cdn.example.com/restaurant/7/1.jpg"]', '江南风味，清淡鲜美，招牌菜有西湖醋鱼、东坡肉、龙井虾仁', 1),
('火锅英雄', '火锅', '重庆市渝中区解放碑步行街88号', 29.556742, 106.577145, 98.00, 4.9, '023-63829999', '11:00-凌晨02:00', '["https://cdn.example.com/restaurant/8/1.jpg","https://cdn.example.com/restaurant/8/2.jpg"]', '重庆老火锅，麻辣鲜香，锅底浓郁，毛肚、鸭肠必点', 1);
