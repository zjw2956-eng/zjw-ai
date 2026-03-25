
  -- 使用现有用户 Vega.z (ID=6)
  SET @userId = 6;

  -- 1. 插入测试餐厅（不同菜系）
  INSERT INTO restaurant (name, category, address, avg_price,
  rating, phone, business_hours, description, status) VALUES
  ('川香阁', '川菜', '成都市武侯区', 80.00, 4.8, '028-12345678',
  '10:00-22:00', '正宗川菜', 1),
  ('粤味轩', '粤菜', '广州市天河区', 150.00, 4.6, '020-87654321',
  '11:00-21:00', '地道粤菜', 1),
  ('寿司之家', '日料', '上海市浦东新区', 200.00, 4.9,
  '021-11111111', '11:30-22:00', '新鲜日料', 1),
  ('麻辣诱惑', '川菜', '重庆市渝中区', 90.00, 4.7, '023-22222222',
  '10:00-23:00', '重庆火锅', 1),
  ('湘菜馆', '湘菜', '长沙市岳麓区', 70.00, 4.5, '0731-33333333',
  '09:00-21:00', '湖南风味', 1);

  -- 2. 插入测试订单（用户多次点川菜，少量粤菜和日料）
  INSERT INTO order_info (order_no, user_id, restaurant_id,
  reservation_time, people_count, contact_name, contact_phone,
  special_request, status) VALUES
  ('ORD20260325001', @userId, 1, '2026-03-20 18:00:00', 4, 'Vega.z',
   '13800138000', '不要辣', 2),
  ('ORD20260325002', @userId, 1, '2026-03-15 19:00:00', 2, 'Vega.z',
   '13800138000', NULL, 2),
  ('ORD20260325003', @userId, 4, '2026-03-10 20:00:00', 3, 'Vega.z',
   '13800138000', '微辣', 2),
  ('ORD20260325004', @userId, 2, '2026-03-05 12:00:00', 2, 'Vega.z',
   '13800138000', NULL, 2),
  ('ORD20260325005', @userId, 1, '2026-02-28 18:30:00', 4, 'Vega.z',
   '13800138000', NULL, 1),
  ('ORD20260325006', @userId, 3, '2026-02-20 19:00:00', 2, 'Vega.z',
   '13800138000', '要芥末', 2);

  -- 3. 插入测试评价（对应上面的订单，需要先获取订单ID）
  -- 注意：这里假设订单ID是自增的，实际需要根据插入后的ID调整
  INSERT INTO review (user_id, restaurant_id, order_id, rating,
  taste_rating, environment_rating, service_rating, content, images,
   status)
  SELECT
      @userId,
      o.restaurant_id,
      o.id,
      CASE
          WHEN o.order_no = 'ORD20260325001' THEN 5
          WHEN o.order_no = 'ORD20260325002' THEN 5
          WHEN o.order_no = 'ORD20260325003' THEN 4
          WHEN o.order_no = 'ORD20260325004' THEN 4
          WHEN o.order_no = 'ORD20260325006' THEN 5
      END as rating,
      CASE
          WHEN o.order_no = 'ORD20260325001' THEN 5
          WHEN o.order_no = 'ORD20260325002' THEN 5
          WHEN o.order_no = 'ORD20260325003' THEN 5
          WHEN o.order_no = 'ORD20260325004' THEN 4
          WHEN o.order_no = 'ORD20260325006' THEN 5
      END as taste_rating,
      CASE
          WHEN o.order_no = 'ORD20260325001' THEN 4
          WHEN o.order_no = 'ORD20260325002' THEN 5
          WHEN o.order_no = 'ORD20260325003' THEN 3
          WHEN o.order_no = 'ORD20260325004' THEN 5
          WHEN o.order_no = 'ORD20260325006' THEN 5
      END as environment_rating,
      CASE
          WHEN o.order_no = 'ORD20260325001' THEN 5
          WHEN o.order_no = 'ORD20260325002' THEN 4
          WHEN o.order_no = 'ORD20260325003' THEN 4
          WHEN o.order_no = 'ORD20260325004' THEN 4
          WHEN o.order_no = 'ORD20260325006' THEN 5
      END as service_rating,
      CASE
          WHEN o.order_no = 'ORD20260325001' THEN
  '非常好吃，麻婆豆腐很正宗！'
          WHEN o.order_no = 'ORD20260325002' THEN
  '第二次来了，还是很喜欢'
          WHEN o.order_no = 'ORD20260325003' THEN
  '火锅很辣，环境一般'
          WHEN o.order_no = 'ORD20260325004' THEN
  '粤菜做得不错，环境很好'
          WHEN o.order_no = 'ORD20260325006' THEN
  '日料超级新鲜，强烈推荐！'
      END as content,
      NULL as images,
      1 as status
  FROM order_info o
  WHERE o.user_id = @userId
    AND o.order_no IN ('ORD20260325001', 'ORD20260325002',
  'ORD20260325003', 'ORD20260325004', 'ORD20260325006');
