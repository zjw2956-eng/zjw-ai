-- =============================================
-- 测试数据 - 菜品
-- =============================================

USE `food_ai_system`;

-- =============================================
-- 插入菜品（每个餐厅3-5个菜品）
-- =============================================

-- 蜀香阁川菜馆的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(1, '水煮鱼', 88.00, 'https://cdn.example.com/dish/1_1.jpg', '鲜嫩鱼片，麻辣鲜香，配菜丰富', '热菜', 1, 1),
(1, '毛血旺', 68.00, 'https://cdn.example.com/dish/1_2.jpg', '麻辣烫口，食材多样，下饭神器', '热菜', 1, 1),
(1, '宫保鸡丁', 48.00, 'https://cdn.example.com/dish/1_3.jpg', '鸡肉嫩滑，花生酥脆，酸甜微辣', '热菜', 0, 1),
(1, '麻婆豆腐', 32.00, 'https://cdn.example.com/dish/1_4.jpg', '豆腐嫩滑，麻辣适中，经典川菜', '热菜', 0, 1);

-- 粤味轩的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(2, '白切鸡', 98.00, 'https://cdn.example.com/dish/2_1.jpg', '皮爽肉滑，原汁原味，配姜葱酱', '凉菜', 1, 1),
(2, '烧鹅', 128.00, 'https://cdn.example.com/dish/2_2.jpg', '皮脆肉嫩，色泽金黄，粤菜经典', '热菜', 1, 1),
(2, '虾饺', 58.00, 'https://cdn.example.com/dish/2_3.jpg', '皮薄馅大，鲜虾饱满，晶莹剔透', '点心', 1, 1),
(2, '叉烧', 88.00, 'https://cdn.example.com/dish/2_4.jpg', '色泽红亮，肥瘦相间，蜜汁香甜', '热菜', 0, 1),
(2, '艇仔粥', 38.00, 'https://cdn.example.com/dish/2_5.jpg', '料足味鲜，绵滑可口，广式早茶', '主食', 0, 1);

-- 湘味人家的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(3, '剁椒鱼头', 88.00, 'https://cdn.example.com/dish/3_1.jpg', '鱼肉鲜嫩，剁椒香辣，湘菜招牌', '热菜', 1, 1),
(3, '小炒肉', 48.00, 'https://cdn.example.com/dish/3_2.jpg', '肉质鲜嫩，辣椒爆香，下饭必备', '热菜', 1, 1),
(3, '口味虾', 78.00, 'https://cdn.example.com/dish/3_3.jpg', '麻辣鲜香，虾肉Q弹，夜宵首选', '热菜', 1, 1);

-- 樱花日料的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(4, '三文鱼刺身', 128.00, 'https://cdn.example.com/dish/4_1.jpg', '新鲜三文鱼，入口即化，配芥末酱油', '刺身', 1, 1),
(4, '寿司拼盘', 168.00, 'https://cdn.example.com/dish/4_2.jpg', '多种寿司组合，精致美味，摆盘精美', '寿司', 1, 1),
(4, '天妇罗', 88.00, 'https://cdn.example.com/dish/4_3.jpg', '外酥里嫩，不油不腻，配萝卜泥', '炸物', 0, 1),
(4, '味增汤', 28.00, 'https://cdn.example.com/dish/4_4.jpg', '味道鲜美，豆腐嫩滑，暖胃佳品', '汤类', 0, 1);

-- 老北京炸酱面的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(5, '炸酱面', 28.00, 'https://cdn.example.com/dish/5_1.jpg', '酱香浓郁，面条劲道，老北京味道', '主食', 1, 1),
(5, '卤煮火烧', 35.00, 'https://cdn.example.com/dish/5_2.jpg', '汤汁浓郁，火烧软糯，北京特色', '小吃', 1, 1),
(5, '爆肚', 48.00, 'https://cdn.example.com/dish/5_3.jpg', '脆嫩爽口，配芝麻酱，京味小吃', '小吃', 0, 1);

-- 意式西餐厅的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(6, '菲力牛排', 268.00, 'https://cdn.example.com/dish/6_1.jpg', '澳洲牛肉，口感细腻，配黑椒汁', '主菜', 1, 1),
(6, '意大利面', 88.00, 'https://cdn.example.com/dish/6_2.jpg', '面条Q弹，酱汁浓郁，多种口味', '主食', 1, 1),
(6, '玛格丽特披萨', 98.00, 'https://cdn.example.com/dish/6_3.jpg', '饼底酥脆，芝士拉丝，经典口味', '主食', 0, 1),
(6, '提拉米苏', 58.00, 'https://cdn.example.com/dish/6_4.jpg', '口感绵密，咖啡香浓，意式甜品', '甜品', 0, 1);

-- 江南小厨的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(7, '西湖醋鱼', 128.00, 'https://cdn.example.com/dish/7_1.jpg', '鱼肉鲜嫩，酸甜适口，杭州名菜', '热菜', 1, 1),
(7, '东坡肉', 88.00, 'https://cdn.example.com/dish/7_2.jpg', '肥而不腻，入口即化，色泽红亮', '热菜', 1, 1),
(7, '龙井虾仁', 98.00, 'https://cdn.example.com/dish/7_3.jpg', '虾仁鲜嫩，茶香清雅，江南特色', '热菜', 1, 1);

-- 火锅英雄的菜品
INSERT INTO `dish` (`restaurant_id`, `name`, `price`, `image`, `description`, `category`, `is_recommend`, `status`) VALUES
(8, '毛肚', 48.00, 'https://cdn.example.com/dish/8_1.jpg', '新鲜毛肚，七上八下，脆嫩爽口', '涮菜', 1, 1),
(8, '鸭肠', 38.00, 'https://cdn.example.com/dish/8_2.jpg', '鲜嫩脆爽，涮10秒即可，火锅必点', '涮菜', 1, 1),
(8, '虾滑', 58.00, 'https://cdn.example.com/dish/8_3.jpg', '手打虾滑，Q弹鲜美，老少皆宜', '涮菜', 1, 1),
(8, '麻辣锅底', 68.00, 'https://cdn.example.com/dish/8_4.jpg', '牛油锅底，麻辣鲜香，重庆正宗', '锅底', 1, 1);
