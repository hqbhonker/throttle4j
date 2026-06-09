# Throttle4j 快速上手教程

> 一篇帮助你在 5 分钟内上手 throttle4j —— 轻量级 Java 分布式限流库的教程。

## 什么是 Throttle4j？

Throttle4j 是一款为 Java 应用设计的轻量级、高性能分布式限流库。无论你是构建 API 网关、微服务还是普通 Web 应用，throttle4j 都能为你提供可靠的速率控制能力。

核心亮点：

- **四大经典限流算法**：固定窗口、滑动窗口、令牌桶、漏桶，开箱即用
- **双存储后端**：内存模式（单机）+ Redis 模式（分布式集群）
- **Spring Boot 深度集成**：一个 `@RateLimit` 注解搞定一切
- **线程安全 & 高性能**：基于原子操作和 Lua 脚本，无锁设计
- **优雅降级**：Redis 不可用时自动降级到本地存储

## 环境要求

- Java 11+
- Maven 3.6+ 或 Gradle 6+
- （可选）Redis 6+，仅分布式模式需要

---

## 一、添加依赖

### Spring Boot 项目（推荐）

**Maven:**

```xml
<dependency>
    <groupId>com.throttle4j</groupId>
    <artifactId>throttle4j-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'com.throttle4j:throttle4j-spring-boot-starter:0.1.0'
```

### 纯 Java 项目（无 Spring 依赖）

```xml
<dependency>
    <groupId>com.throttle4j</groupId>
    <artifactId>throttle4j-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## 二、编程式使用（Pure Java）

不依赖 Spring，你也可以直接使用 throttle4j 核心 API：

```java
import com.throttle4j.algorithm.DefaultRateLimiterFactory;
import com.throttle4j.core.*;
import com.throttle4j.store.InMemoryStore;

public class QuickStartDemo {
    public static void main(String[] args) {
        // 1. 创建内存存储
        try (InMemoryStore store = new InMemoryStore()) {
            RateLimiterFactory factory = new DefaultRateLimiterFactory(store);

            // 2. 配置限流器：令牌桶算法，容量5，每秒补充5个令牌
            RateLimiterConfig config = RateLimiterConfig.builder()
                    .algorithm(Algorithm.TOKEN_BUCKET)
                    .limit(5)
                    .refillRate(5)
                    .build();
            RateLimiter limiter = factory.create(config);

            // 3. 尝试获取令牌
            String key = "user:1001";
            for (int i = 1; i <= 7; i++) {
                RateLimitResult result = limiter.tryAcquire(key);
                System.out.printf("请求 #%d -> 允许=%s, 剩余=%d%n",
                        i, result.isAllowed(), result.getRemaining());
            }
        }
    }
}
```

运行结果示例：

```
请求 #1 -> 允许=true, 剩余=4
请求 #2 -> 允许=true, 剩余=3
请求 #3 -> 允许=true, 剩余=2
请求 #4 -> 允许=true, 剩余=1
请求 #5 -> 允许=true, 剩余=0
请求 #6 -> 允许=false, 剩余=0
请求 #7 -> 允许=false, 剩余=0
```

---

## 三、Spring Boot 集成（5 分钟上手）

### 3.1 添加配置

在 `application.yml` 中添加：

```yaml
throttle4j:
  enabled: true
  default-algorithm: TOKEN_BUCKET
  default-limit: 100
  default-window: 1m
  store-type: memory          # 单机使用 memory，分布式使用 redis
```

### 3.2 使用 @RateLimit 注解

在 Controller 方法上加上 `@RateLimit` 注解即可启用限流：

```java
import com.throttle4j.core.Algorithm;
import com.throttle4j.spring.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class UserController {

    // 滑动窗口：10秒内最多5次请求
    @RateLimit(limit = 5, window = "10s", algorithm = Algorithm.SLIDING_WINDOW)
    @GetMapping("/hello")
    public String hello() {
        return "Hello, Throttle4j!";
    }

    // 令牌桶：自定义key，每分钟最多10次
    @RateLimit(key = "'user:' + #userId", limit = 10, window = "1m", algorithm = Algorithm.TOKEN_BUCKET)
    @GetMapping("/users/{userId}")
    public String getUser(@PathVariable Long userId) {
        return "User " + userId;
    }
}
```

### 3.3 注解参数详解

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `key` | 限流键，支持 SpEL 表达式（如 `#userId`） | `ClassName.methodName` |
| `limit` | 窗口内允许的最大请求数 | `100` |
| `window` | 时间窗口（支持 `500ms`、`1s`、`30s`、`1m`、`1h`） | `1m` |
| `algorithm` | 限流算法 | `SLIDING_WINDOW` |
| `permits` | 每次调用消耗的许可数 | `1` |
| `fallbackMethod` | 限流触发时的降级方法名 | 无 |

### 3.4 处理限流异常

当请求超限时，框架会抛出 `RateExceededException`。你可以用全局异常处理器优雅返回 429 状态码：

```java
import com.throttle4j.core.RateExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateExceededException.class)
    public ResponseEntity<String> handleRateExceeded(RateExceededException e) {
        long retryAfter = e.getResult() != null
                ? Math.max(1L, e.getResult().getRetryAfterMillis() / 1000L)
                : 1L;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfter))
                .body("请求过于频繁，请稍后重试");
    }
}
```

---

## 四、启用全局 Web 拦截器

除了注解方式，你还可以对所有 URL 启用全局限流拦截器：

```yaml
throttle4j:
  enabled: true
  default-algorithm: SLIDING_WINDOW
  default-limit: 200
  default-window: 1m
  store-type: memory
  web:
    enabled: true                      # 开启全局拦截
    include-patterns:
      - /api/**
    exclude-patterns:
      - /api/health
      - /api/public/**
```

拦截器会自动在响应头中注入标准限流信息：

```
X-RateLimit-Limit: 200
X-RateLimit-Remaining: 199
X-RateLimit-Reset: 1686000060
Retry-After: 5          （仅在被限流时返回）
```

---

## 五、切换到 Redis 分布式模式

在微服务/集群场景下，你需要让多个节点共享限流状态。只需两步：

### 5.1 添加 Redis 依赖

```xml
<dependency>
    <groupId>com.throttle4j</groupId>
    <artifactId>throttle4j-redis</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 5.2 修改配置

```yaml
throttle4j:
  enabled: true
  store-type: redis
  default-algorithm: TOKEN_BUCKET
  default-limit: 100
  default-window: 1m
  redis:
    host: 127.0.0.1
    port: 6379
    password: your-password    # 无密码可省略
    database: 0
    key-prefix: "throttle4j:"
```

throttle4j 使用 Lettuce 客户端 + Lua 脚本保证原子性，并内置 **优雅降级**：当 Redis 不可用时自动切换到本地内存存储，恢复后再切回。

---

## 六、如何选择限流算法？

| 算法 | 适用场景 | 特点 |
|------|---------|------|
| **Token Bucket（令牌桶）** | API 网关、允许突发流量 | 平滑限速，允许短时间突发 |
| **Sliding Window（滑动窗口）** | 需要精确配额控制 | 精度高，无边界突刺问题 |
| **Fixed Window（固定窗口）** | 简单计数场景 | 实现简单，有窗口边界突刺风险 |
| **Leaky Bucket（漏桶）** | 需要恒定速率输出 | 匀速处理，适合下游敏感场景 |

**推荐选择**：大多数 API 限流场景使用 `TOKEN_BUCKET`；对公平性要求高用 `SLIDING_WINDOW`。

---

## 七、完整示例项目

throttle4j 提供了一个开箱即用的示例模块 `throttle4j-examples`，包含：

- 纯 Java 编程式用法
- Spring Boot + `@RateLimit` 注解用法
- 全局异常处理示例

运行示例：

```bash
git clone https://github.com/hqbhonker/throttle4j.git
cd throttle4j
mvn clean install -DskipTests
cd throttle4j-examples
mvn spring-boot:run
```

访问测试：

```bash
# 正常访问
curl http://localhost:8080/api/hello

# 快速连续请求，观察限流效果
for i in {1..10}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/hello; done
```

---

## 八、生产环境部署注意事项

### 8.1 Redis 高可用配置

生产环境下 Redis 是限流状态的核心存储，务必保证其可用性：

- **使用 Redis Sentinel 或 Cluster**：避免单点故障，确保 Redis 挂掉后能自动切换
- **启用 `fallback-on-error`**：throttle4j 的 `FallbackRateLimitStore` 会在 Redis 异常时自动降级到本地内存，但要注意此时限流变为节点粒度
- **监控降级次数**：通过 `FallbackRateLimitStore.getFallbackInvocations()` 监控降级频率，及时发现 Redis 问题

```yaml
throttle4j:
  store-type: redis
  redis:
    host: redis-sentinel.internal
    port: 26379
    password: ${THROTTLE4J_REDIS_PASSWORD}
    key-prefix: "prod:throttle4j:"
```

### 8.2 Key 设计规范

- **避免热点 Key**：如果所有请求都用同一个 key，会造成 Redis 单 key 热点。建议按用户/租户/接口维度拆分
- **Key 命名规范**：推荐 `{service}:{resource}:{identifier}` 格式，如 `order-service:createOrder:user_123`
- **注意 Key 总量**：内存模式下 InMemoryStore 有后台清理线程（默认 5 分钟空闲后回收），但大量 key 仍会占用较多内存

### 8.3 多节点部署

| 部署模式 | 推荐 store-type | 限流粒度 | 说明 |
|---------|----------------|---------|------|
| 单节点 | `memory` | 进程级 | 最高性能，无网络开销 |
| 多节点/K8s | `redis` | 集群级 | 所有节点共享配额 |
| 混合模式 | `redis` + fallback | 主集群级，故障时退化为节点级 | 推荐生产使用 |

### 8.4 安全建议

- **不要暴露限流配置接口**：避免攻击者利用配置接口调整限流阈值
- **敏感信息外化**：Redis 密码等使用环境变量注入，不要硬编码在 `application.yml` 中
- **日志脱敏**：限流 key 中如果包含用户 ID 等敏感信息，注意日志级别控制

---

## 九、性能调优建议

### 9.1 算法选择对性能的影响

| 算法 | 内存占用 | 单次操作耗时 | 适合 QPS |
|------|---------|------------|----------|
| Fixed Window | 最低（1 个计数器） | ~O(1) | 百万级 |
| Token Bucket | 低（几个浮点数） | ~O(1) | 百万级 |
| Sliding Window | 中等（10 个 slot） | ~O(N) N=slot数 | 十万级 |
| Leaky Bucket | 低（1 个浮点数） | ~O(1) | 百万级 |

### 9.2 InMemoryStore 调优

InMemoryStore 内置后台清理线程，可以通过构造参数调整：

```java
// 高并发场景：缩短空闲 TTL 和清理间隔，减少内存占用
InMemoryStore store = new InMemoryStore(
    TimeUnit.MINUTES.toMillis(2),   // idleMillis: 2分钟无访问则回收
    TimeUnit.SECONDS.toMillis(30)   // cleanupInterval: 每30秒清理一次
);
```

**调优建议**：
- 高并发 + 大量不同 key：缩短 `idleMillis` 到 1~2 分钟
- 低并发 + 少量固定 key：可以加大 `idleMillis` 到 10 分钟
- 不要将 `cleanupInterval` 设置得过短（<10s），避免频繁 GC 压力

### 9.3 Redis Store 调优

- **连接池大小**：Lettuce 默认使用单连接多路复用，高并发场景建议配置连接池
- **Key 前缀长度**：保持较短的 `key-prefix`，减少网络传输和 Redis 内存开销
- **Lua 脚本原子性**：所有限流操作通过 Lua 脚本在 Redis 端执行，无需客户端加锁
- **网络延迟**：Redis 与应用部署在同一内网/AZ，控制 RTT < 1ms

### 9.4 Spring AOP 开销优化

- `@RateLimit` 注解基于 Spring AOP 拦截，首次调用有 SpEL 解析开销，后续命中缓存
- 对极高频内部方法（如批处理循环中），建议使用编程式 API 而非注解
- 如果不需要全局 Web 拦截器，将 `throttle4j.web.enabled` 保持为 `false`（默认值），减少拦截器链开销

### 9.5 JVM 参数建议

```bash
# 高并发限流场景建议的 JVM 参数
-XX:+UseG1GC
-XX:MaxGCPauseMillis=20
-Xms512m -Xmx512m           # 根据 key 数量适当调整
```

---

## 十、常见问题 FAQ

### Q1: 为什么请求没有被限流？

**可能原因**：
1. `throttle4j.enabled` 设置为 `false` 或未配置
2. `@RateLimit` 注解放在了非 public 方法上（Spring AOP 只拦截 public 方法）
3. 同一个 Bean 内部调用（this 调用不经过代理，AOP 不生效）
4. 检查 key 是否唯一：如果 SpEL 解析失败，会回退到 `ClassName.methodName`，可能导致不同参数共享配额

**解决方案**：
```java
// ❌ 内部调用不生效
public void outer() {
    this.inner(); // AOP 不拦截
}

@RateLimit(limit = 5, window = "1m")
public void inner() { ... }

// ✅ 通过注入自身或拆分到不同 Bean 解决
@Autowired
private MyService self;

public void outer() {
    self.inner(); // 走代理，AOP 生效
}
```

### Q2: Redis 挂了会怎样？

如果使用了 `throttle4j-redis` 模块，框架内置了 `FallbackRateLimitStore` 降级机制：
- Redis 不可用时，自动切换到本地 `InMemoryStore`，限流依然生效（但变为节点粒度）
- 每次降级会记录 WARN 日志：`Primary rate limit store unavailable, falling back`
- Redis 恢复后，下一次请求会自动切回 Redis

> 注意：降级期间各节点独立计数，实际集群总流量可能超过配置阈值（= 阈值 × 节点数）。

### Q3: `window` 参数支持哪些格式？

支持以下时间简写格式：
- `500ms` — 500 毫秒
- `1s` / `30s` — 秒
- `1m` / `5m` — 分钟
- `1h` — 小时

在编程式 API 中使用 `windowSeconds(long)` 或 `windowMillis(long)` 设置。

### Q4: 如何实现按用户/IP 维度限流？

利用 SpEL 表达式动态设置 key：

```java
// 按用户 ID 限流
@RateLimit(key = "'user:' + #userId", limit = 100, window = "1m")
@GetMapping("/api/data/{userId}")
public String getData(@PathVariable String userId) { ... }

// 按 IP 限流（需通过 HttpServletRequest 获取）
@RateLimit(key = "'ip:' + #request.remoteAddr", limit = 60, window = "1m")
@GetMapping("/api/public")
public String publicApi(HttpServletRequest request) { ... }
```

### Q5: 令牌桶的 refillRate 怎么配？

在 `@RateLimit` 注解中不直接暴露 `refillRate`，框架会自动从 `limit / windowSeconds` 推导：
- `limit = 100, window = "1m"` → refillRate ≈ 1.67 tokens/s
- `limit = 10, window = "1s"` → refillRate = 10 tokens/s

如需自定义 refillRate，使用编程式 API：
```java
RateLimiterConfig config = RateLimiterConfig.builder()
    .algorithm(Algorithm.TOKEN_BUCKET)
    .limit(50)         // 桶容量
    .refillRate(10)    // 每秒补充 10 个令牌
    .build();
```

### Q6: 能否在同一个方法上叠加多个限流规则？

目前 `@RateLimit` 是单一注解，不支持重复标注。如需多维度限流（如同时限制 IP 和用户），建议：
1. 使用编程式 API 组合多个 limiter
2. 在 Web 拦截器做全局 IP 限流 + 注解做用户级限流，两层叠加

### Q7: 如何在测试中 mock 限流？

```java
@SpringBootTest
class MyServiceTest {

    @MockBean
    private RateLimiterRegistry registry;

    @Test
    void testNormal() {
        // Mock 一个始终允许的 limiter
        RateLimiter mockLimiter = mock(RateLimiter.class);
        when(mockLimiter.tryAcquire(anyString(), anyInt()))
            .thenReturn(RateLimitResult.allowed(99, System.currentTimeMillis() + 60000));
        when(registry.get(anyString())).thenReturn(mockLimiter);
        // ... 正常测试逻辑
    }
}
```

### Q8: InMemoryStore 会导致 OOM 吗？

InMemoryStore 内置了后台守护线程，默认每 60 秒清理一次 5 分钟内无访问的 key。正常使用不会 OOM，但如果：
- key 数量极大（百万级不同用户）且访问持续活跃
- 选择了 Sliding Window（每个 key 额外占用 10 个 slot）

建议监控 `store.size()` 并适当缩短 `idleMillis`，或直接使用 Redis 存储。

### Q9: 限流触发后客户端应该如何处理？

建议客户端遵循标准 HTTP 429 协议：
1. 读取响应头 `Retry-After`（秒数），等待后重试
2. 实现指数退避策略，避免重试风暴
3. 在网关层可以使用 `X-RateLimit-Remaining` 做客户端预判，主动降速

### Q10: 支持哪些 Spring Boot 版本？

throttle4j 基于 Spring Boot 2.7.x 开发和测试，兼容：
- Spring Boot 2.7.x ✅
- Spring Boot 3.x ✅（需使用 Jakarta 命名空间，但 throttle4j 不依赖 Servlet API）
- Java 11、17 均通过 CI 验证

---

## 总结

通过本教程，你学会了：

1. 在纯 Java 项目中使用 throttle4j 进行编程式限流
2. 在 Spring Boot 项目中通过 `@RateLimit` 注解实现声明式限流
3. 配置全局 Web 拦截器进行 URL 级别限流
4. 切换到 Redis 实现分布式限流
5. 根据业务场景选择合适的限流算法
6. 生产环境下的部署策略和高可用保障
7. 针对不同场景的性能调优方法

throttle4j 的设计理念是 **简单、轻量、可插拔**。如果你正在寻找一个不依赖重型框架的 Java 限流方案，throttle4j 是一个不错的选择。

---

**相关链接**：
- [GitHub 仓库](https://github.com/hqbhonker/throttle4j)
- [CHANGELOG](../CHANGELOG.md)
- [贡献指南](../CONTRIBUTING.md)
