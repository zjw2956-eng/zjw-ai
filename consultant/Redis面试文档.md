# Redis 面试文档（结合项目实战）

> 项目：智能美食推荐与餐厅管理系统  
> 目标：回答面试中关于 Redis 的所有问题，答案结合项目实际使用，体现工程思维而非背八股。

---

# 一、Redis 基础数据结构及项目中的使用

## 面试问：Redis 有哪些数据结构，你项目里用了哪些？

### String（字符串）
项目中使用最广泛的结构，覆盖以下场景：

| 场景 | Key 示例 | 说明 |
|------|----------|------|
| 用户 Token | user:token:{userId} | JWT 登录态存储 |
| 用户信息缓存 | user:info:{userId} | 30分钟过期 |
| 餐厅信息缓存 | restaurant:info:{id} | 72小时过期 |
| 订单详情缓存 | order:detail:{userId}:{orderNo} | 防穿透 |
| 评价详情缓存 | review:detail:{id} | 防穿透 |
| 餐厅摘要缓存 | restaurant:ai:summary:{id} | AI 生成，24小时 |
| MQ 消息去重 | mq:consumed:{msgId} | SETNX，72小时 |
| MQ 消息元数据备份 | mq:msgId:{msgId} | 生产者确认重试用 |
| AI 会话记忆 | chat:memory:{memoryId} | LangChain4j 多轮对话 |
| 订单号序列 | order:seq:{yyyyMMdd} | INCR 原子自增 |
| 知识库初始化标记 | knowledge:initialized | RAG 向量库一次性初始化 |

### ZSet（有序集合）
| 场景 | Key | Score 含义 |
|------|-----|-----------|
| 热门餐厅榜单 | hot:restaurant:rank | 近30天订单数 |
| 订单超时延时队列 | order:no_show:delay:zset | 超时时间戳（Unix ms） |

### Hash（哈希）
热门榜单详情缓存用 Hash 存储，key 为 hot:restaurant:detail:{id}，value 是 RestaurantVO 的 JSON 字符串。

---

# 二、缓存三大问题及项目解决方案

## 面试问：缓存穿透、击穿、雪崩分别是什么，你怎么解决的？

### 1. 缓存穿透

定义：查询一个数据库里根本不存在的数据，缓存永远不会命中，每次都打到 DB。

项目方案：在 CacheClient 的 queryWithPassThrough 方法里，DB 查询返回 null 时，不是什么都不写，而是往 Redis 写入一个空值哨兵字符串 __NULL__，设置短 TTL（2分钟加随机抖动）。下次同样的请求进来，读到哨兵直接返回 null，不再查 DB。这样即便有人恶意用不存在的 ID 刷接口，也只有第一次会打到数据库，后续全部被 Redis 拦截。

### 2. 缓存击穿

定义：热点 key 过期瞬间，大量并发请求同时打到 DB，DB 压力骤增。

项目方案：在 CacheClient 的 queryWithMutex 方法里，缓存未命中时先用 Redisson 分布式锁抢占，只有拿到锁的那一个线程去查 DB 重建缓存，其他线程等待或重试。

关键细节是拿到锁之后要做 Double Check，也就是再查一次缓存。原因是：在等待锁的这段时间里，可能已经有线程把缓存重建好了，如果不做二次检查就直接查 DB，等于白白多了一次数据库查询。所以拿到锁后先查缓存，有数据直接返回，没有才真正回源。

### 3. 缓存雪崩

定义：大量 key 同时过期，或 Redis 宕机，导致所有请求同时打到 DB。

项目方案：CacheClient 里所有写缓存的地方都加了随机 TTL 抖动，用 withRandomTtl 方法在基础过期时间上加一个随机值。比如餐厅缓存基础 72 小时，加上 0 到 10 秒的随机值，不同 key 的过期时间就错开了，不会在同一时刻集体失效。

---

# 三、Redis 实现分布式锁

## 面试问：你项目里怎么用 Redis 实现分布式锁的？

项目使用 Redisson 封装的分布式锁，而不是手写 SETNX + expire，原因是 Redisson 解决了以下问题：SETNX 和 expire 是两步操作，中间如果宕机锁就永远不会释放；Redisson 有看门狗机制可以自动续期，防止业务未完成锁就过期；同时支持可重入，同一线程可以多次加锁。

### 项目中的锁场景

#### 1. 缓存击穿防护（CacheClient）

用 redissonClient.getLock(lockKey) 获取锁对象，然后调 tryLock(waitTime, leaseTime, TimeUnit.SECONDS)。waitTime 设为 0 表示拿不到锁立即返回 false，不排队等待；leaseTime 设为具体秒数表示持有这么长时间后自动释放。

#### 2. 热门榜单刷新（HotRankServiceImpl）

用 redissonClient.getLock(Constants.REDIS_LOCK_RESTAURANT_HOT_RANK_KEY) 加锁，tryLock(0, 10, TimeUnit.SECONDS)，拿不到锁直接 return 跳过。选择"拿不到锁直接返回"而不是等待，是因为榜单刷新是幂等操作，第一个线程刷完数据就是最新的，其他线程等待没有意义。

#### 3. 用户注册并发安全（UserServiceImpl）

锁的 key 是 Constants.REDIS_LOCK_USER_REGISTER_KEY 拼上 username，锁粒度按用户名维度，不同用户名互不影响。拿不到锁说明同一用户名正在注册，直接抛出"请勿重复提交"的业务异常。

#### 4. 评价提交并发安全（ReviewServiceImpl）

锁的 key 是 Constants.REDIS_LOCK_REVIEW_SUBMIT 拼上 userId 和 orderId，锁粒度精确到用户加订单这一次评价行为，避免同一订单被重复评价。

### 释放锁的正确姿势

释放锁必须放在 finally 块里，并且释放前要调 lock.isHeldByCurrentThread() 判断当前线程是否还持有锁。这个判断不能省略：如果锁因为超时已经自动释放，其他线程已经拿到了这把锁，此时如果直接 unlock 就会误释放别人的锁，造成并发安全问题。

### 为什么锁 + 唯一索引要同时用？

| 层 | 职责 | 能否单独用 |
|----|------|-----------|
| Redisson 锁 | 串行化并发请求，减少无效 DB 写入 | 不够，锁失效或多节点问题仍可能重复 |
| DB 唯一索引 | 最终兜底，绝对不允许脏数据 | 可以，但高并发下无效写入多，DB 压力大 |

结论：锁是第一道门，索引是最后一道墙，两者缺一不可。

---

# 四、Redis ZSet 实现延时队列

## 面试问：你项目里怎么实现订单超时自动取消的？

项目里有两类超时场景，没有机械地用同一种方案：

### 场景 A：订单确认超时（RabbitMQ TTL + 死信队列）
触发点明确是下单时，延迟固定 30 分钟，适合 MQ 原生 TTL + DLX，结构清晰。

### 场景 B：到店未消费超时（Redis ZSet + 定时扫描）
触发时间与 reservationTime 强相关，不是固定延迟，需要二次状态校验，多实例部署下还需要防重复处理，所以用 Redis ZSet 实现。

ZSet 实现思路：订单创建时计算超时时间戳，用 ZADD 把 orderId 作为 member、超时时间戳作为 score 写入 order:no_show:delay:zset。定时任务 OrderNoShowCancelTask 每 5 秒执行一次，用 ZRANGEBYSCORE 取出 score 小于当前时间戳的订单，也就是已经到期的订单。对每个到期订单加 Redisson 锁（key 为 order:no_show:lock:{orderId}），然后执行数据库条件更新，WHERE 条件里带上 status = CONFIRMED，保证只有确认状态的订单才会被取消。

两层并发保护：第一层是 Redis 分布式锁，防止多实例重复处理同一订单；第二层是数据库条件更新，即便锁层面有竞争，DB 层面也保证状态正确。

---

# 五、Redis 实现热门榜单

## 面试问：热门榜单怎么设计的？

数据结构选 ZSet，key 是 hot:restaurant:rank，score 是近 30 天订单数，member 是 restaurantId。ZSet 天然按 score 排序，取 TopN 直接用 ZREVRANGE 就行，不需要额外排序逻辑。

刷新流程：定时任务 HotRankScheduler 每日凌晨 2 点触发，也支持手动调接口刷新。流程是先加 Redisson 锁防并发（拿不到直接返回），然后删除旧 ZSet 和旧详情缓存，接着查询近 30 天已完成订单按餐厅聚合，批量写入新 ZSet，最后预热 Top30 餐厅详情缓存。

查询流程：先用 ZREVRANGE hot:restaurant:rank 0 N-1 取 TopN 餐厅 ID，再用 multiGet 批量查详情缓存，缓存未命中的 ID 回源 DB，然后用 Redis Pipeline 批量写回缓存，一次网络 IO 搞定。

为什么用 Pipeline 而不是循环 expire？循环 expire 是 N 次网络 IO，每次都要等服务器响应再发下一条命令。Pipeline 是把所有命令打包成一次请求发送，服务器批量执行后一次性返回结果，显著减少网络往返延迟。具体做法是调 stringRedisTemplate.executePipelined，在回调里用 connection.stringCommands().set 把 SET 和 TTL 合并成一条命令，N 个 key 只产生 1 次网络 IO。

---

# 六、Redis 实现订单号唯一生成

## 面试问：分布式环境下怎么保证订单号唯一？

方案是 ORDER 前缀加上 yyyyMMdd 日期再加上 Redis INCR 序列号，格式类似 ORDER202404090000000001。

具体做法：用 Redis INCR 命令对 order:seq:{今天日期} 这个 key 做原子自增，拿到自增后的序列号 seq。只有当 seq 等于 1 的时候（也就是这个 key 第一次被创建时）才调 expire 设置 2 天过期，后续自增不再重复设置过期时间。最后把序列号格式化成 10 位补零字符串拼接成完整订单号。

为什么不用本地锁？本地 synchronized 只能限制单机，多实例部署下各自自增会产生重复序列号。Redis INCR 是原子操作，天然跨实例唯一，不需要额外加锁。

为什么 seq == 1 才 expire？如果每次自增都调 expire，高并发下会有大量无效的 expire 命令打到 Redis。只在第一次创建 key 时设置过期，后续自增不再重复设置，既保证了 key 会过期，又避免了无效命令的开销。

---

# 七、Redis 实现 MQ 消息去重（幂等）

## 面试问：RabbitMQ 消息重复消费怎么处理？

RabbitMQ 是 at-least-once 投递，重复消费不是偶发 bug，是分布式消息系统的常见现象，必须在业务层做幂等处理。

项目方案是 MqDedupService 用 Redis SETNX 做消费幂等。核心方法 tryMarkConsumed 接收 msgId，用 setIfAbsent 往 Redis 写入 mq:consumed:{msgId} 这个 key，同时设置 72 小时过期。setIfAbsent 底层就是 SETNX，只有 key 不存在时才写入成功，返回 true 表示首次消费，返回 false 表示重复消费。

消费者的处理流程：收到消息后先提取 msgId，调 tryMarkConsumed 判断是否首次消费。如果返回 false 说明重复，直接 ACK 跳过。如果返回 true 说明首次，执行业务逻辑。业务执行异常时调 rollback 方法删除 Redis 标记，允许消息重试；业务成功后 ACK 确认消息。

---

# 八、Redis 实现 MQ 生产者可靠发送

## 面试问：怎么保证 MQ 消息不丢失？

生产者侧的做法：发送消息前先生成 msgId，把消息的 exchange、routingKey、body、retryCount 等元数据存入 Redis（key 为 mq:msgId:{msgId}），然后发送 MQ 并携带 msgId 作为 CorrelationData。

ConfirmCallback 是交换机确认回调，消息到达交换机后触发，这里做延迟删除 Redis 备份的操作。ReturnsCallback 是路由失败回调，消息无法路由到队列时触发，这里标记 route_failed 并触发重试逻辑。

重试逻辑：从 Redis 取出消息元数据，重新发送，最多重试 3 次（Constants.MAX_RETRY_COUNT），超过后写入失败缓存 mq:retry:failed:msg:{msgId}。

为什么 ConfirmCallback 里要延迟删除？ConfirmCallback（交换机确认）和 ReturnsCallback（路由失败）可能几乎同时触发。如果 ConfirmCallback 立刻删除 Redis 备份，ReturnsCallback 就拿不到消息元数据来重试了。所以延迟 1 秒，先检查是否有路由失败标记，再决定是否删除。

---

# 九、Redis 实现 AI 多轮对话记忆

## 面试问：AI 多轮对话的上下文怎么持久化的？

方案是自定义 RedisChatMemoryStore 实现 LangChain4j 的 ChatMemoryStore 接口。

存储时调 ChatMessageSerializer.messagesToJson 把消息列表序列化成 JSON 字符串，写入 chat:memory:{memoryId} 这个 key，设置 1 天 TTL。读取时从 Redis 取出 JSON，调 ChatMessageDeserializer.messagesFromJson 反序列化回消息列表。

遇到的坑：消息顺序不满足模型预期。LangChain4j 要求第一条非系统消息必须是 UserMessage，如果从 Redis 恢复后顺序不对（比如第一条是 AI 回复），模型会出现上下文理解异常。

修复方案：在 RedisChatMemoryStore 里加了 fixMessageOrder 逻辑，恢复消息后检查并修正顺序，保证第一条非系统消息是用户消息。

---

# 十、Redis 性能优化实践

## 面试问：你项目里有哪些 Redis 性能优化？

### 1. Pipeline 批量操作

热门榜单预热和缓存回填时，用 executePipelined 把所有 set 加 expire 合并成一次网络请求发送，避免 N 次网络 IO。在 executePipelined 的回调里调 connection.stringCommands().set，把 key、value、过期时间、SET 选项一次性传入，相当于把 SET 和 EXPIRE 合并成一条命令，N 个 key 只产生 1 次网络往返。

### 2. multiGet 批量读取

热门榜单查询时，用 stringRedisTemplate.opsForValue().multiGet(detailKeys) 一次取多个详情缓存，而不是循环调 get，同样是减少网络 IO 次数。

### 3. 随机 TTL 防雪崩

CacheClient 里的 withRandomTtl 方法在基础过期时间上加随机值，用 ThreadLocalRandom.current().nextInt(randomSeconds) 生成随机抖动量，避免大量 key 同时失效。

### 4. 条件 expire（订单号生成）

order:seq:{date} 只在 seq 等于 1 时设置过期，避免高并发下重复调用 expire 产生无效命令。

### 5. 空值哨兵防穿透

用固定字符串 __NULL__ 作为哨兵，不走 JSON 序列化，读取时直接字符串比较，性能比序列化空对象更好。

---

# 十一、Redis 配置与运维

## 面试问：Redis 在你项目里怎么配置的？

项目里 Redis 连接配置在 application.yaml 里，host 默认 localhost，port 默认 6379，各环境在对应 profile 里覆盖。

Redisson 配置在 RedissonConfig.java 里，用 Config 对象调 useSingleServer().setAddress 指定单节点地址，然后 Redisson.create(config) 创建客户端 Bean。

项目里两个 Redis 客户端并存：StringRedisTemplate 用于普通缓存读写，操作简单性能好；RedissonClient 用于分布式锁，利用 Redisson 的看门狗、可重入等高级特性。两者各司其职，不混用。

---

# 十二、面试高频追问

## Q：Redis 和 Memcached 的区别？
Redis 支持多种数据结构（String/Hash/List/Set/ZSet），支持持久化（RDB/AOF），支持主从复制和集群，支持 Lua 脚本原子操作。Memcached 只支持 String，不支持持久化。

## Q：Redis 为什么快？
第一是纯内存操作；第二是单线程模型（命令执行），避免锁竞争；第三是 IO 多路复用（epoll），支持高并发连接；第四是高效的数据结构（跳表、压缩列表等）。

## Q：Redis 持久化方式？
RDB 是定期快照，恢复快，但可能丢失最后一次快照后的数据。AOF 是记录每条写命令，数据更完整，但文件大、恢复慢。混合持久化是 Redis 4.0 引入的，AOF 文件前半段是 RDB，后半段是增量 AOF，兼顾两者优点。

## Q：Redis 集群方案？
主从复制是读写分离，主节点写，从节点读。哨兵模式在主从基础上加了自动故障转移，主节点宕机后自动选举新主。Cluster 模式是数据分片，16384 个 slot 分布在多个节点，支持水平扩展。

## Q：Redis 内存淘汰策略？

| 策略 | 说明 |
|------|------|
| allkeys-lru | 所有 key 中淘汰最近最少使用的（推荐缓存场景） |
| volatile-lru | 只淘汰设置了过期时间的 key 中最近最少使用的 |
| allkeys-random | 随机淘汰 |
| noeviction | 不淘汰，内存满了直接报错 |

## Q：SETNX 和 SET NX 的区别？
SETNX 是老命令，不支持设置过期时间，需要单独调 EXPIRE，两步操作不原子，中间如果宕机锁就永远不会释放。SET key value NX EX seconds 是原子操作，推荐使用。Spring 的 setIfAbsent(key, value, ttl, unit) 底层就是这个原子命令。

## Q：Redisson 的看门狗机制是什么？
加锁时如果不指定 leaseTime（或指定为 -1），Redisson 会启动一个后台线程（看门狗），每隔 lockWatchdogTimeout 的三分之一（默认 10 秒）检查锁是否还被持有，如果是则自动续期。防止业务未完成锁就过期被其他线程抢占。

项目里 tryLock(0, 10, TimeUnit.SECONDS) 指定了 leaseTime=10，不会触发看门狗，10 秒后锁自动释放。这是有意为之：榜单刷新、缓存重建等操作不应该无限持有锁。

---

# 十三、一句话总结（面试收尾用）

我项目里 Redis 不是只用来做简单 KV 缓存，而是覆盖了缓存三大问题防护（穿透/击穿/雪崩）、ZSet 延时队列、ZSet 热门榜单、INCR 分布式序列号、SETNX 消息幂等去重、Redisson 分布式锁（多场景）、AI 会话记忆持久化，以及 Pipeline 批量优化。每个场景都是根据业务特点选型，而不是套模板。
