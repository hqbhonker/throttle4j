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

## 总结

通过本教程，你学会了：

1. 在纯 Java 项目中使用 throttle4j 进行编程式限流
2. 在 Spring Boot 项目中通过 `@RateLimit` 注解实现声明式限流
3. 配置全局 Web 拦截器进行 URL 级别限流
4. 切换到 Redis 实现分布式限流
5. 根据业务场景选择合适的限流算法

throttle4j 的设计理念是 **简单、轻量、可插拔**。如果你正在寻找一个不依赖重型框架的 Java 限流方案，throttle4j 是一个不错的选择。

---

**相关链接**：
- [GitHub 仓库](https://github.com/hqbhonker/throttle4j)
- [CHANGELOG](../CHANGELOG.md)
- [贡献指南](../CONTRIBUTING.md)
