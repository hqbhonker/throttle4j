# 从零设计一个 Java 分布式限流库：throttle4j 架构解析

> 本文深入解析 [throttle4j](https://github.com/hqbhonker/throttle4j) 的设计思路——一个轻量级、高性能、易集成的 Java 分布式限流库。我们将从架构分层、算法实现、分布式一致性、Spring Boot 集成等多个维度，剖析每一个关键设计决策背后的"为什么"。

---

## 一、引言：为什么又造了一个限流轮子？

在微服务架构中，限流是保障系统稳定性的第一道防线。无论是防止恶意爬虫、控制 API 调用频率、保护下游慢服务，还是应对突发流量洪峰，限流都扮演着不可或缺的角色。

现有方案各有局限：**Sentinel** 功能强大但体量庞大，引入全家桶对轻量级项目是负担；**Guava RateLimiter** 只支持单机令牌桶，无法分布式使用；**Bucket4j** 设计优秀但 API 复杂，学习曲线陡峭。

throttle4j 的定位很明确：**轻量（核心零依赖）、高性能（原子操作 + Lua 脚本）、易集成（一个注解搞定）**。它不是要替代 Sentinel，而是为那些"只需要限流、不需要全套流量治理"的场景提供恰到好处的解决方案。

---

## 二、整体架构设计

### 分层架构：Core → Store → Integration

throttle4j 采用经典的三层架构，每层职责清晰、边界分明：

```
┌─────────────────────────────────────────────────────┐
│         throttle4j-spring-boot-starter              │
│    (自动配置 / @RateLimit AOP / Web 拦截器)          │
├─────────────────────────────────────────────────────┤
│              throttle4j-redis                        │
│    (Redis + Lua 分布式存储 / 故障降级)               │
├─────────────────────────────────────────────────────┤
│              throttle4j-core                         │
│    (算法抽象 / RateLimiter API / 内存存储)           │
└─────────────────────────────────────────────────────┘
```

### 核心设计原则

1. **接口抽象**：`RateLimiter`、`RateLimitStore`、`RateLimiterFactory` 三个核心接口构成扩展骨架
2. **策略模式**：算法通过枚举 + 工厂实现热切换，新增算法只需一个类
3. **关注点分离**：算法逻辑由 Store 层实现，Limiter 层只做委托——这意味着同一套算法实现可以同时运行在内存和 Redis 上

### 模块划分的考量

为什么不把 Redis 直接放进 core？因为 **core 模块零外部依赖**（除 SLF4J），应用只做单机限流时不会被强制引入 Lettuce 等 Redis 客户端。这是对"最小依赖原则"的实践。

---

## 三、限流算法深入解析

throttle4j 内置四种经典限流算法，所有算法都统一收敛到 `RateLimitStore.tryAcquire()` 一个方法：

```java
// throttle4j-core: com/throttle4j/store/RateLimitStore.java
public interface RateLimitStore {
    RateLimitResult tryAcquire(String key, int permits, RateLimiterConfig config);
    void reset(String key);
}
```

### 1. 固定窗口（Fixed Window）

**原理**：将时间划分为固定长度的窗口，窗口内计数不超过阈值即放行。

```
  窗口1 [0s-60s]       窗口2 [60s-120s]
  ┌──────────────┐    ┌──────────────┐
  │ count: 98/100│    │ count: 0/100 │
  └──────────────┘    └──────────────┘
                  ↑ 窗口重置，计数归零
```

**实现关键**（摘自 `InMemoryStore.acquireFixedWindow`）：

```java
// throttle4j-core: com/throttle4j/store/InMemoryStore.java
synchronized (st) {
    if (now - st.windowStart >= windowMillis) {
        st.windowStart = now;
        st.count = 0L;  // 窗口过期，重置计数
    }
    if (st.count + permits > limit) {
        return RateLimitResult.rejected(remaining, resetAt, retryAfter);
    }
    st.count += permits;
    return RateLimitResult.allowed(limit - st.count, resetAt);
}
```

**适用场景**：简单计数、粗粒度配额。但要注意**临界突刺问题**——在窗口交界处可能出现两倍阈值的瞬时流量。

### 2. 滑动窗口（Sliding Window）

**原理**：将窗口切分为多个子槽位（默认 10 个），通过滚动淘汰过期槽位来近似实现精确的滑动窗口。

```
  子槽位 (每个 6s，共覆盖 60s 窗口)
  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┬──┐
  │12│ 8│15│ 7│10│ 9│11│13│ 6│ 9│ = 100
  └──┴──┴──┴──┴──┴──┴──┴──┴──┴──┘
       ← 最旧的槽位滚出窗口外被清零
```

**实现关键**：

```java
// throttle4j-core: com/throttle4j/store/InMemoryStore.java
static final int SLOTS = 10;

long slotSize = Math.max(1L, windowMillis / SlidingWindowState.SLOTS);
long currentSlotId = now / slotSize;
long firstValid = currentSlotId - SlidingWindowState.SLOTS + 1;
// 淘汰过期槽位
for (int i = 0; i < SlidingWindowState.SLOTS; i++) {
    if (st.slotIds[i] < firstValid) {
        st.slotIds[i] = -1L;
        st.counts[i] = 0L;
    } else {
        total += st.counts[i];
    }
}
```

**设计取舍**：10 个子槽位是精确度和内存占用的平衡点。槽位越多越精确，但内存开销线性增长。对于绝大多数场景，10 个子槽位的近似误差在 10% 以内，完全可以接受。

### 3. 令牌桶（Token Bucket）

**原理**：桶中持有令牌，请求消耗令牌，令牌以固定速率补充，桶满则溢出。允许一定程度的突发。

```
  容量: 100 tokens, 补充速率: 10 tokens/s
  
  ┌─────────────┐
  │ ○○○○○○○○○○ │ ← 当前 73 个令牌
  │ ○○○○○○○○○○ │
  │ ○○○○○○○○○○ │    请求消耗 1 token
  │ ○○○○○○○○○○ │    10 tokens/s 持续补充
  │ ...         │
  └─────────────┘
```

**懒计算的优雅**：throttle4j 没有用后台线程定时添加令牌，而是在每次 `tryAcquire` 时计算经过的时间和应补充的令牌数：

```java
// throttle4j-core: com/throttle4j/store/InMemoryStore.java
double elapsedSec = Math.max(0L, now - st.lastRefillMillis) / 1000.0;
double refilled = elapsedSec * refillRate;
st.tokens = Math.min((double) limit, st.tokens + refilled);
st.lastRefillMillis = now;
```

这种"懒计算"方式避免了后台线程的资源消耗，也避免了对不活跃 key 的无意义计算。

### 4. 漏桶（Leaky Bucket）

**原理**：请求进入桶中排队，以恒定速率"漏出"处理。桶满则拒绝新请求。

```
  请求涌入 ↓         恒速漏出 ↓
  ┌─────────┐        ┌─────────┐
  │ ████████ │ ──→   │   │ │   │ ──→ 稳定输出
  │ ████████ │        │   ↓ ↓   │
  └─────────┘        └─────────┘
   桶满时拒绝            恒定速率
```

**实现关键**：漏桶本质上是令牌桶的"镜像"——令牌桶桶里装的是"可用配额"，漏桶桶里装的是"积压水位"：

```java
// throttle4j-core: com/throttle4j/store/InMemoryStore.java
double leakPerMs = (double) limit / (double) windowMillis;
long elapsed = Math.max(0L, now - st.lastLeakMillis);
double leaked = elapsed * leakPerMs;
st.level = Math.max(0.0, st.level - leaked);  // 水位下降
if (st.level + permits <= limit) {
    st.level += permits;  // 注入新请求
    return RateLimitResult.allowed(...);
}
```

### 算法适用场景对比

| 算法 | 突发容忍 | 精确度 | 实现复杂度 | 最佳场景 |
|------|---------|--------|-----------|---------|
| 固定窗口 | 有突刺 | 低 | 最低 | 简单计数 |
| 滑动窗口 | 平滑 | 高 | 中 | API 配额 |
| 令牌桶 | 允许突发 | 高 | 中 | 网关限流 |
| 漏桶 | 完全平滑 | 高 | 中 | 保护下游 |

---

## 四、分布式限流的挑战与解决方案

### 分布式环境下的一致性问题

单机限流用 `synchronized` 就够了，但在分布式环境下，多个节点共享一个限流计数器，面临的核心问题是：**"读取当前计数 → 判断是否超限 → 更新计数"这三步必须是原子的**。如果分开执行，并发请求可能同时读到相同的旧值，全部通过检查后各自 +1，导致实际放行数远超阈值。

### 为什么选择 Redis + Lua？

Redis 单线程模型天然保证了命令的串行执行，而 **Lua 脚本将多条 Redis 命令打包为一个原子操作**，在脚本执行期间不会被其他客户端命令打断。这正好解决了"读-判-写"的原子性问题。

相比 Redis 事务（MULTI/EXEC），Lua 脚本的优势在于：
- 可以在脚本内做条件判断（事务不支持）
- 只需一次网络往返（事务需要多次）
- 语义更清晰，更易维护

### Lua 脚本设计（以 Token Bucket 为例）

```lua
-- throttle4j-redis: src/main/resources/scripts/token_bucket.lua
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])      -- tokens per second
local permits = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

-- 从 Hash 中读取当前状态
local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(data[1])
local lastRefill = tonumber(data[2])

if tokens == nil or lastRefill == nil then
    tokens = capacity          -- 首次访问，满桶开始
    lastRefill = now
end

-- 懒计算：根据经过的时间补充令牌
local elapsed = now - lastRefill
if elapsed < 0 then elapsed = 0 end
local refilled = (elapsed / 1000.0) * rate
tokens = tokens + refilled
if tokens > capacity then tokens = capacity end
lastRefill = now

-- 尝试消费
local allowed = 0
if tokens >= permits then
    tokens = tokens - permits
    allowed = 1
end

-- 回写状态并设置过期时间
redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(lastRefill))
local ttlSec = math.ceil((capacity / rate) * 2)  -- 安全边际
redis.call('EXPIRE', key, ttlSec)

return {allowed, math.floor(tokens), 0}
```

**几个设计亮点**：
1. 使用 **Hash 结构**存储多字段状态（tokens + lastRefill），比多个 key 更紧凑
2. **自动过期**：TTL 设为"满桶充满时间 × 2"，不活跃的 key 自动清除，防止 Redis 内存泄漏
3. **时间由客户端传入**：避免多节点时钟不一致依赖 Redis 服务端时间的问题

### 故障降级机制

分布式系统中 Redis 不是永远可用的。throttle4j 提供了 `FallbackRateLimitStore`：

```java
// throttle4j-redis: com/throttle4j/redis/FallbackRateLimitStore.java
public RateLimitResult tryAcquire(String key, int permits, RateLimiterConfig config) {
    try {
        return primary.tryAcquire(key, permits, config);
    } catch (Exception e) {
        fallbackInvocations.incrementAndGet();
        log.warn("Primary rate limit store unavailable, falling back. key={}", key);
        return fallback.tryAcquire(key, permits, config);
    }
}
```

设计哲学是"**降级但不失控**"：Redis 挂掉时，系统不是完全放开限流（fail-open），也不是直接拒绝所有请求（fail-closed），而是回退到本地内存限流。虽然各节点独立计数会导致全局限流精度下降，但至少每个节点仍然有保护。

### 性能考量：减少 Redis 往返

每次限流判断只需 **1 次 Redis 往返**（一个 EVAL 调用）。Lua 脚本在 Redis 端执行完所有逻辑后返回结果，不存在多次 RTT 叠加的问题。对于令牌桶，这意味着"计算补充 → 判断余量 → 扣减 → 回写"全在 Redis 内完成。

---

## 五、Spring Boot 集成设计

### 零配置自动装配

throttle4j-spring-boot-starter 利用 Spring Boot 的条件装配机制，做到**引入依赖即生效**：

```java
// throttle4j-spring-boot-starter: autoconfigure/Throttle4jAutoConfiguration.java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "throttle4j", name = "enabled", 
                       havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Throttle4jProperties.class)
public class Throttle4jAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitStore rateLimitStore(Throttle4jProperties properties) {
        if (properties.getStoreType() == StoreType.REDIS) {
            RateLimitStore redisStore = tryCreateRedisStore(properties);
            if (redisStore != null) return redisStore;
        }
        return new InMemoryStore();  // 兜底：默认内存存储
    }
}
```

关键设计：
- `matchIfMissing = true`：不配置任何属性就能用
- `@ConditionalOnMissingBean`：所有 Bean 都可被用户覆盖
- **反射加载 Redis Store**：避免 starter 对 `throttle4j-redis` 的编译时依赖，classpath 上有 Redis 模块就用，没有就自动降级到内存

### @RateLimit 注解 + AOP 的声明式体验

```java
// throttle4j-spring-boot-starter: annotation/RateLimit.java
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    String key() default "";           // 支持 SpEL
    long limit() default 100;
    String window() default "1m";      // 人性化时间表达
    Algorithm algorithm() default Algorithm.SLIDING_WINDOW;
    int permits() default 1;
    String fallbackMethod() default "";  // 降级方法
}
```

**AOP 切面**在方法执行前拦截，解析 SpEL 表达式生成限流 key，从 Registry 获取（或懒创建）Limiter，执行 `tryAcquire`：

```java
// throttle4j-spring-boot-starter: aop/RateLimitAspect.java
@Around("@annotation(com.throttle4j.spring.annotation.RateLimit)")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    // 1. 解析 SpEL key
    String key = resolveKey(annotation, pjp, method);
    // 2. 获取或创建 Limiter
    RateLimiter limiter = obtainLimiter(key, annotation);
    // 3. 尝试获取许可
    RateLimitResult result = limiter.tryAcquire(key, permits);
    // 4. 通过则放行，否则走 fallback 或抛异常
    if (result.isAllowed()) return pjp.proceed();
    if (!annotation.fallbackMethod().isEmpty()) {
        return invokeFallback(pjp, method, annotation.fallbackMethod(), result);
    }
    throw new RateExceededException(key, result);
}
```

SpEL 表达式缓存在 `ConcurrentHashMap` 中避免重复解析，参数名通过 `DefaultParameterNameDiscoverer` 发现，支持 `#userId`、`#p0`、`#a0` 等多种引用方式。

### 配置属性的设计哲学

时间窗口采用**人性化字符串**而非毫秒数：

```java
// throttle4j-spring-boot-starter: util/WindowParser.java
// 支持：500ms、30s、1m、1h、1d
public static long parseToMillis(String window) { ... }
```

这使得 YAML 配置和注解参数都很直观：`window = "30s"` 比 `windowMillis = 30000` 可读性好得多。

### Web 拦截器与 HTTP 标准响应头

全局 Web 拦截器自动在每个响应中写入标准限流头：

```java
// throttle4j-spring-boot-starter: web/RateLimitInterceptor.java
response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetAt()));
// 被拒绝时额外返回 Retry-After
response.setHeader("Retry-After", String.valueOf(retryAfterSec));
response.setStatus(429);
```

这遵循了 [IETF RateLimit Header Fields](https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/) 草案标准，客户端可以据此实现智能退避。

---

## 六、关键设计决策

### 为什么用 Builder 模式做配置？

`RateLimiterConfig` 采用不可变对象 + Builder 模式：

```java
// throttle4j-core: com/throttle4j/core/RateLimiterConfig.java
RateLimiterConfig config = RateLimiterConfig.builder()
    .algorithm(Algorithm.TOKEN_BUCKET)
    .limit(100)
    .windowSeconds(60)
    .refillRate(10)
    .build();
```

**为什么不用构造器？** 因为不同算法需要的参数不同（令牌桶需要 refillRate，其他算法不需要），Builder 模式允许按需设置参数，并在 `build()` 时做验证。构造后不可变保证了多线程下的安全共享。

### 为什么 Store 层独立抽象？

一个不常见的设计是：**算法逻辑放在 Store 里而不是 RateLimiter 里**。`RateLimiter` 只是一个薄薄的委托层：

```java
// throttle4j-core: com/throttle4j/algorithm/AbstractStoreBackedRateLimiter.java
@Override
public RateLimitResult tryAcquire(String key, int permits) {
    return store.tryAcquire(key, permits, config);
}
```

这个决策的原因是：**内存存储和 Redis 存储的算法实现完全不同**。内存版用 `synchronized`，Redis 版用 Lua 脚本。如果算法逻辑在 Limiter 层，就无法优雅地切换存储后端。将算法下沉到 Store 层，使得同一个 `TokenBucketRateLimiter` 类既能对接内存 Store 也能对接 Redis Store，实现了真正的可插拔。

### 线程安全的保证策略

throttle4j 在不同层面选择了不同的并发策略：

| 层面 | 策略 | 原因 |
|------|------|------|
| `RateLimiterRegistry` | `ConcurrentHashMap.computeIfAbsent` | 无锁注册，first-write-wins 语义 |
| `InMemoryStore` 各算法 | `synchronized(state)` | 粒度细（per-key 锁），简单可靠 |
| `InMemoryStore` 清理 | `ScheduledExecutorService` | 后台守护线程，不阻塞业务 |
| `FallbackRateLimitStore` | `AtomicLong` 计数降级次数 | 无锁统计 |
| Redis Store | Lua 脚本原子性 | Redis 单线程保证 |

为什么内存 Store 用 `synchronized` 而不是 CAS？因为每次限流操作涉及"读 → 计算 → 写"多步状态更新，CAS 循环在高竞争下退化严重，`synchronized` 在 JDK 11+ 的偏向锁 / 轻量级锁优化下反而更高效。同时锁粒度是 **per-key** 的（每个 key 独立的 state 对象），不同 key 之间完全无竞争。

### 过期清理机制的权衡

内存 Store 会积累大量不再活跃的 key。throttle4j 采用**被动过期 + 主动清理**双重策略：

```java
// throttle4j-core: com/throttle4j/store/InMemoryStore.java
// 后台定时任务（默认 60s 一次）清理空闲超过 5 分钟的 key
this.cleanupExecutor.scheduleAtFixedRate(
    this::cleanup, cleanupIntervalMillis, cleanupIntervalMillis, TimeUnit.MILLISECONDS);
```

为什么不在每次 `tryAcquire` 时清理？因为遍历所有 key 的开销不可接受（O(n)），会直接影响每次请求的 RT。后台线程以可配置的间隔执行清理，对业务请求零影响。守护线程在 JVM 关闭时自动退出，同时实现了 `AutoCloseable` 接口支持手动关闭。

---

## 七、性能与扩展性

### 内存限流的性能基准

内存 Store 的核心路径非常短：一次 `ConcurrentHashMap.computeIfAbsent` + 一次 `synchronized` 块内的简单算术运算。在 per-key 粒度的锁下，不同 key 之间零竞争。单线程下每次 `tryAcquire` 的执行时间在**亚微秒级**。

### Redis 限流的延迟分析

Redis Store 每次操作涉及：
- 1 次网络 RTT（执行 Lua EVAL）
- Redis 端：Hash/String/SortedSet 操作，纳秒级

在局域网环境下（RTT < 1ms），单次限流判断总延迟通常在 **1-2ms**。这比业务逻辑本身的耗时低一个数量级，对吞吐的影响可以忽略。

### 未来扩展方向

- **集群模式**：支持 Redis Cluster 分片，更高吞吐
- **动态配置**：运行时通过配置中心动态调整限流参数，无需重启
- **监控集成**：输出 Micrometer metrics，接入 Prometheus/Grafana 可视化
- **更多存储后端**：Memcached、Hazelcast 等分布式缓存适配

---

## 八、总结与展望

throttle4j 的设计哲学可以概括为三个词：**简洁、正确、可扩展**。

- **简洁**：核心 API 只有一个 `tryAcquire` 方法，Spring Boot 场景下一个注解搞定
- **正确**：内存用锁保证、Redis 用 Lua 脚本保证原子性，故障时优雅降级
- **可扩展**：Store 接口可插拔、算法通过工厂模式扩展、所有 Spring Bean 可覆盖

如果你的项目需要一个不那么"重"的限流方案，欢迎试试 throttle4j。

**项目地址**：[https://github.com/hqbhonker/throttle4j](https://github.com/hqbhonker/throttle4j)

```xml
<dependency>
    <groupId>com.throttle4j</groupId>
    <artifactId>throttle4j-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

欢迎 Star、Issue 和 PR。关于开发流程和代码规范，请参阅 [CONTRIBUTING.md](https://github.com/hqbhonker/throttle4j/blob/main/CONTRIBUTING.md)。

---

*作者：throttle4j team | 协议：Apache License 2.0*
