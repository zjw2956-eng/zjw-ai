# Redisson 面试文档（结合项目实战）

> 项目：智能美食推荐与餐厅管理系统  
> 目标：全面回答面试中关于 Redisson 的问题，答案结合项目实际使用，体现工程思维。

整体结构：为什么用 Redisson 不手写 SETNX — 对比表，5个维度；可重入锁底层原理 — Hash 结构 + Lua 脚本，这是面试最爱问的；看门狗机制 — 触发条件、工作原理、项目里为什么没用；tryLock vs lock — 区别 + 项目四个场景各自的处理策略对比表；四个使用场景详解 — 注册、评价、缓存击穿、榜单刷新；isHeldByCurrentThread 为什么不能省；锁粒度设计；Redisson vs 手写 SETNX 的对比；高频追问；一段话总结。

重点推荐讲第 2、5、8 这三块，技术含量最高，也最能体现你不是只会调 API。

---

# 一、Redisson 是什么，为什么用它而不是手写 SETNX？

Redisson 是基于 Redis 的 Java 客户端，在 Jedis/Lettuce 的基础上封装了大量分布式数据结构和工具，最常用的是它的分布式锁。

## 为什么不手写 SETNX + EXPIRE？

手写方案有几个根本性问题：

| 问题     | 手写 SETNX                              | Redisson                       |
| -------- | --------------------------------------- | ------------------------------ |
| 原子性   | SETNX + EXPIRE 两步，中间宕机锁永不释放 | SET NX EX 原子操作             |
| 锁续期   | 业务超时锁自动释放，其他线程抢占        | 看门狗自动续期                 |
| 可重入   | 不支持，同一线程重复加锁会死锁          | 支持，基于 Hash 计数           |
| 释放安全 | 可能误删别人的锁                        | isHeldByCurrentThread() 保护   |
| 锁类型   | 只有基本互斥锁                          | 公平锁、读写锁、联锁等         |

项目里统一用 Redisson，不手写 SETNX，原因就是上面这些。

---

# 二、Redisson 可重入锁底层原理

## 面试问：Redisson 分布式锁是怎么实现的？

底层不是简单的 SET key value NX EX，而是用 Redis Hash 结构存储锁信息。key 是锁的名称（比如 lock:user:register:zhangsan），field 是线程标识（UUID + 线程ID，格式是 {uuid}:{threadId}），value 是重入次数（整数）。

**加锁逻辑（Lua 脚本原子执行，用文字描述）**：

加锁时先判断这个 key 是否存在。如果不存在，说明锁空闲，就用 hset 创建一个 Hash，field 是当前线程标识，value 设为 1，再用 pexpire 设置过期时间，返回 nil 表示加锁成功。如果 key 存在，再用 hexists 判断 field 是不是当前线程。如果是，说明是同一线程重入，就用 hincrby 把 value 加 1，同时重置过期时间，返回 nil 表示加锁成功。如果 key 存在但 field 不是当前线程，说明锁被别人持有，直接返回 pttl（剩余过期时间，毫秒），表示加锁失败，调用方可以根据这个值决定等多久再重试。

**解锁逻辑（Lua 脚本原子执行，用文字描述）**：

解锁时先用 hexists 判断这个 field 是不是当前线程持有的。如果不是，直接返回 nil，不做任何操作（防止误删他人的锁）。如果是，就用 hincrby 把 value 减 1，得到新的重入计数。如果计数还大于 0，说明还有外层调用没退出，只重置过期时间，不删锁。如果计数归零了，说明所有重入都退出了，用 del 删除整个 key，然后向一个专用 channel 发布解锁消息，通知其他正在等待的线程可以来抢锁了。

**为什么用 Lua 脚本？**  
Lua 脚本在 Redis 中是原子执行的，整个加锁或解锁过程中不会有其他命令插入，保证了多步操作的原子性。这是 Redisson 比手写 SETNX 更可靠的核心原因之一。

---

# 三、看门狗（Watchdog）机制

## 面试问：Redisson 的看门狗是什么？

看门狗是 Redisson 的自动续期机制，防止业务还没执行完锁就过期被其他线程抢占。

**触发条件**：调用 tryLock 或 lock 时不指定 leaseTime（或指定为 -1）。

**工作原理**：

加锁成功后，Redisson 在后台启动一个定时任务（TimerTask）。这个任务每隔 lockWatchdogTimeout 除以 3 的时间（默认 lockWatchdogTimeout 是 30000 毫秒，所以每隔约 10 秒）检查一次锁是否还被当前线程持有。如果是，就把过期时间重置回 30 秒。线程主动释放锁后，看门狗自动停止。

默认参数：lockWatchdogTimeout 是 30000ms（30秒），续期间隔是 10000ms（10秒）。

## 项目里为什么没用看门狗？

项目里所有锁都指定了 leaseTime 为 10 秒，比如 lock.tryLock 传入 waitTime=0、leaseTime=10、TimeUnit.SECONDS。只要指定了 leaseTime，看门狗就不会启动。

原因是项目里的锁场景（注册、评价提交、缓存重建、榜单刷新）都是短操作，10 秒绰绰有余。指定 leaseTime 的好处有两点：一是不需要后台线程，资源开销更小；二是即使业务异常没有手动释放，10 秒后锁自动释放，不会死锁。

什么时候用看门狗：业务执行时间不确定（比如大批量数据处理），无法预估 leaseTime 时才用。

---

# 四、tryLock vs lock 的区别

## 面试问：tryLock 和 lock 有什么区别，你项目里为什么用 tryLock？

| 方法                                 | 行为                                     | 适用场景               |
| ------------------------------------ | ---------------------------------------- | ---------------------- |
| lock()                               | 拿不到锁就阻塞等待，直到拿到为止         | 必须串行执行，不能跳过 |
| tryLock(waitTime, leaseTime, unit)   | 等待 waitTime 后还拿不到就返回 false     | 可以跳过或快速失败     |

项目里全部用 tryLock(0, 10, TimeUnit.SECONDS)，waitTime=0 意味着拿不到立即返回 false，不排队等待。

**各场景的处理策略不同**：

| 场景         | 拿不到锁的处理          | 原因                         |
| ------------ | ----------------------- | ---------------------------- |
| 用户注册     | 抛异常"获取锁失败"      | 并发注册同一用户名，直接拒绝 |
| 评价提交     | 抛异常"请勿重复提交"    | 同一订单并发提交，直接拒绝   |
| 热门榜单刷新 | 直接 return             | 幂等操作，已有线程在刷就跳过 |
| 缓存击穿防护 | sleep 50ms 后重试读缓存 | 等待重建完成后直接读缓存结果 |

---

# 五、项目中 Redisson 的四个使用场景

## 场景一：用户注册并发安全（UserServiceImpl）

**问题**：先查用户名是否存在，再插入。并发下两个请求同时通过查询，都插入同一用户名。

**锁粒度**：按用户名维度，key 是 Constants.REDIS_LOCK_USER_REGISTER_KEY 拼接 username，比如 lock:user:register:zhangsan，不同用户名互不影响。

**流程**：通过 redissonClient.getLock(lockKey) 获取锁对象，调用 tryLock(0, 10, TimeUnit.SECONDS)，拿不到锁直接抛 RuntimeException("获取锁失败")。拿到锁后，先查 userMapper.selectCount 判断用户名是否已存在，存在则抛 BusinessException("用户名重复")，不存在则执行 userMapper.insert。finally 块里加 isHeldByCurrentThread() 判断后再 unlock。

**两层防护**：第一层 Redisson 锁串行化并发请求，减少无效 DB 写入；第二层 DB 唯一索引（uk_username、uk_phone）最终兜底，捕获 DuplicateKeyException。

## 场景二：评价提交并发安全（ReviewServiceImpl）

**问题**：同一用户对同一订单并发提交两次，绕过"已评价"校验。

**锁粒度**：按用户+订单维度，key 是 Constants.REDIS_LOCK_REVIEW_SUBMIT 拼接 userId 和 orderId，比如 lock:review:submit:1001100，精确到一次评价行为。

**流程**：tryLock(0, 10, TimeUnit.SECONDS) 拿不到锁抛 BusinessException(ResultCode.BAD_REQUEST, "请勿重复提交评价")。拿到锁后查是否已评价，再插入评价记录。finally 块同样加 isHeldByCurrentThread() 判断后再 unlock。

## 场景三：缓存击穿防护（CacheClient.queryWithMutex）

**问题**：热点 key 过期瞬间，大量并发请求同时打到 DB。

**锁粒度**：按数据 ID 维度，key 是 lock:restaurant:{id}，不同餐厅互不影响。

**关键设计 — Double Check**：拿到锁后再查一次缓存，因为等待期间可能已经有线程重建好了。

**流程**：调用 tryLock(0, 10, TimeUnit.SECONDS)。拿不到锁时，sleep 50ms 后重试读缓存，如果缓存已有数据就直接返回，不再尝试加锁。拿到锁后先做 Double Check，再次从 stringRedisTemplate.opsForValue().get(key) 读缓存，有数据直接返回。真正没有数据才回源 DB，查完后调用 setWithRandomTtl 写入缓存（带随机过期时间防雪崩）。

这里 tryLock 拿不到锁不是直接失败，而是 sleep + 重试，因为缓存重建是必须完成的，不能跳过。

## 场景四：热门榜单刷新并发保护（HotRankServiceImpl + HotRankScheduler）

**问题**：定时任务和手动刷新同时触发，多线程并发删旧榜单、查 DB、写 ZSet，数据混乱。多实例部署下，每个实例的定时任务都会在同一时刻触发，还会导致队列堆积重复消息。

**锁粒度**：全局唯一，key 是 Constants.REDIS_LOCK_RESTAURANT_HOT_RANK_KEY（即 lock:hot:rank:refresh），整个刷新操作只允许一个线程执行。

**两把锁的设计**：

| 锁 key | 加锁位置 | 作用 |
|---|---|---|
| lock:hot:rank:refresh:schedule | HotRankScheduler 定时任务 | 多实例下只让一个节点发 MQ 消息 |
| lock:hot:rank:refresh | HotRankService.refreshHotRestaurantRank | 防止消费者并发执行刷新逻辑 |

**流程**：定时任务先抢 `:schedule` 锁，抢到才发 MQ 消息，其余实例直接跳过。消费者收到消息后调用 `refreshHotRestaurantRank`，内部再抢 `lock:hot:rank:refresh` 锁执行实际刷新，拿不到锁直接 return。

拿不到锁直接 return 而不是抛异常，因为榜单刷新是幂等操作，第一个线程刷完数据就是最新的，其他线程没必要等待或报错。

**为什么用 MQ 而不是直接在定时任务里执行**：定时任务只发消息，执行极快，不阻塞调度线程；消费者执行失败会触发 RabbitMQ 自动重试，可靠性更强；刷新逻辑和调度逻辑解耦。

---

# 六、isHeldByCurrentThread() 为什么不能省？

## 面试问：释放锁为什么要加 isHeldByCurrentThread() 判断？

finally 块里的写法是：先判断 locked 为 true，再判断 lock.isHeldByCurrentThread() 为 true，两个条件都满足才调用 lock.unlock()。

**场景分析**：指定了 leaseTime=10 秒，如果业务执行超过 10 秒，锁自动过期释放，此时其他线程已经拿到了这把锁。如果不加 isHeldByCurrentThread() 判断，当前线程的 finally 块会把别人的锁给释放掉，造成锁失效，后续并发请求全部涌入，保护形同虚设。

**两个条件缺一不可**：

- locked：确保是成功加锁的线程才去释放（没拿到锁的线程不应该调 unlock）
- isHeldByCurrentThread()：确保锁还在当前线程手里（防止锁超时后误释放他人的锁）

---

# 七、Redisson 锁的粒度设计

## 面试问：分布式锁的粒度怎么设计？

锁粒度越粗，并发越低；锁粒度越细，并发越高，但 key 管理越复杂。

项目里的粒度设计：

| 场景         | 锁 Key                                 | 粒度          | 原因                           |
| ------------ | -------------------------------------- | ------------- | ------------------------------ |
| 用户注册     | lock:user:register:{username}          | 用户名级别    | 不同用户名完全独立，无需互斥   |
| 评价提交     | lock:review:submit:{userId}{orderId}   | 用户+订单级别 | 同一用户对同一订单才需要互斥   |
| 缓存击穿     | lock:restaurant:{id}                   | 餐厅ID级别    | 不同餐厅缓存重建互不影响       |
| 用户信息缓存 | lock:user:{userId}                     | 用户ID级别    | 同上                           |
| 榜单刷新     | lock:hot:rank:refresh                  | 全局唯一      | 整个榜单只有一份，必须全局互斥 |

**原则**：锁的粒度应该和被保护的资源粒度一致，不要用全局锁保护局部资源。

---

# 八、Redisson vs 手写 SETNX 锁（OrderDelayService 的对比）

项目里订单超时取消（OrderDelayServiceImpl）用的是手写 SETNX，而不是 Redisson。

**手写方案的做法**：加锁时用 stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, 30, TimeUnit.SECONDS)，lockValue 是一个随机 UUID。解锁时用 Lua 脚本原子执行：先用 get 取出 key 的值，如果等于当前线程存入的 lockValue 才执行 del，否则返回 0。这样保证了判断和删除是原子的，不会误删他人的锁。

**为什么这里用手写而不是 Redisson？**  
订单超时扫描是定时任务，每 5 秒扫一批，锁只需要简单的互斥（防止多实例重复处理同一订单），不需要可重入、看门狗等高级特性，手写 SETNX 足够且更轻量。

**手写锁的三个关键点**：加锁用 SET NX EX 原子操作；锁值用 UUID 每次唯一，防止误删；解锁用 Lua 脚本保证判断和删除的原子性。

**面试时可以说**：我项目里两种方案都用了，Redisson 用于需要可重入、看门狗、精细粒度控制的场景；手写 SETNX + Lua 用于简单互斥、对性能要求更高的定时任务场景。选型依据是业务复杂度，不是越高级越好。

---

# 九、Redisson 高频追问

## Q：Redisson 可重入锁和 Java synchronized 可重入有什么区别？

synchronized 的可重入是 JVM 层面，基于线程栈帧计数，只在单 JVM 内有效。Redisson 的可重入是 Redis Hash 层面，field 是 UUID:threadId，跨 JVM 实例也能识别同一线程（但实际上跨实例的线程 ID 不同，所以可重入只在同一 JVM 实例内有意义）。

## Q：Redisson 公平锁和非公平锁的区别？

非公平锁是默认行为，通过 redissonClient.getLock(key) 获取，谁抢到算谁的，可能有线程饥饿。公平锁通过 redissonClient.getFairLock(key) 获取，按请求顺序排队，底层用 Redis List 维护等待队列。

项目里用的都是非公平锁，因为注册、评价提交等场景不需要保证顺序，非公平锁性能更好。

## Q：RedLock 是什么？有什么争议？

RedLock 是 Redis 官方提出的多节点分布式锁算法：向 N 个独立 Redis 节点加锁，超过半数（N/2+1）成功才算加锁成功，防止单节点故障导致锁失效。

争议在于 Martin Kleppmann 指出 RedLock 在时钟漂移、GC 停顿等场景下仍然不安全，Antirez（Redis 作者）对此有反驳，业界至今没有定论。

实际项目里单节点 Redis 足够，没用 RedLock。如果需要强一致性分布式锁，应该用 ZooKeeper 或 etcd。

## Q：Redisson 读写锁是什么？

通过 redissonClient.getReadWriteLock(key) 获取读写锁，提供读写分离的锁语义：多个线程可以同时持有读锁（共享），写锁是排他的，写时不允许读。适合读多写少的场景，比如配置数据的缓存更新。项目里没用到，但面试可以提。

## Q：Redisson 联锁（MultiLock）是什么？

通过 new RedissonMultiLock(lock1, lock2) 把多个 RLock 组合成一个联锁，所有子锁都加锁成功才算整体加锁成功。适合需要同时操作多个资源的场景，防止死锁（按固定顺序加锁）。

## Q：Redisson 锁和数据库悲观锁怎么选？

| 维度     | Redisson 分布式锁        | DB 悲观锁（SELECT FOR UPDATE） |
| -------- | ------------------------ | ------------------------------ |
| 性能     | 高（Redis 内存操作）     | 低（DB 行锁，影响吞吐）        |
| 适用范围 | 跨服务、跨 DB 的资源互斥 | 同一 DB 内的行级互斥           |
| 锁粒度   | 灵活，自定义 key         | 行级                           |
| 死锁风险 | 有（需设置 leaseTime）   | 有（DB 自动检测死锁）          |

项目里选 Redisson 而不是 SELECT FOR UPDATE，原因有两点：注册、评价提交的并发保护不需要锁住 DB 行，锁住"操作"本身就够了；Redisson 锁在 Redis 层面，不占用 DB 连接，性能更好。

---

# 十、面试总结（一段话版本）

我项目里 Redisson 主要用在五个场景：用户注册和评价提交的"先查后插"并发安全、热门榜单刷新的幂等保护、CacheClient 里的缓存击穿防护，以及热门榜单定时任务的多实例防重复发消息。每个场景的锁粒度都不一样，注册按用户名、评价按用户+订单、缓存按数据 ID、榜单用全局锁。榜单场景还用了两把锁：定时任务里一把防止多实例重复发 MQ 消息，Service 里一把防止消费者并发执行刷新逻辑，职责分离。所有场景都用 tryLock(0, 10, TimeUnit.SECONDS)，不等待，拿不到锁根据业务决定是抛异常、直接返回还是 sleep 重试。释放锁时统一加 isHeldByCurrentThread() 判断，防止锁超时后误释放他人的锁。底层原理上，Redisson 用 Redis Hash 存锁信息实现可重入，用 Lua 脚本保证加锁解锁的原子性，指定 leaseTime 时不触发看门狗，适合短操作场景。
