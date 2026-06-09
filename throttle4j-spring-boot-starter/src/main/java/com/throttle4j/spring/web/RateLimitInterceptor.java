package com.throttle4j.spring.web;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiter;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.core.RateLimiterRegistry;
import com.throttle4j.spring.autoconfigure.Throttle4jProperties;
import com.throttle4j.spring.util.WindowParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
 * URL-path based global rate-limit interceptor.
 *
 * <p>The limiter key is derived from the HTTP method and request URI
 * (e.g. {@code "GET:/api/users"}). Standard rate-limit headers are emitted
 * on every response and a {@code 429 Too Many Requests} status is returned
 * when the request is rejected.</p>
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /** {@code X-RateLimit-Limit} response header name. */
    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    /** {@code X-RateLimit-Remaining} response header name. */
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    /** {@code X-RateLimit-Reset} response header name. */
    public static final String HEADER_RESET = "X-RateLimit-Reset";
    /** {@code Retry-After} response header name (seconds). */
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    private final RateLimiterRegistry registry;
    private final Throttle4jProperties properties;

    /**
     * @param registry   shared limiter registry
     * @param properties throttle4j configuration
     */
    public RateLimitInterceptor(RateLimiterRegistry registry, Throttle4jProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Acquire a permit for the current request, set rate-limit headers and
     * short-circuit with HTTP 429 when the call is rejected.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String key = request.getMethod() + ":" + request.getRequestURI();
        RateLimiter limiter = obtainLimiter(key);
        long limit = limiter.getConfig().getLimit();

        RateLimitResult result;
        try {
            result = limiter.tryAcquire(key);
        } catch (RuntimeException ex) {
            log.warn("throttle4j interceptor failed for key {}: {}", key, ex.getMessage());
            return true;
        }

        setRateLimitHeaders(response, result, limit);

        if (!result.isAllowed()) {
            long retryAfterSec = Math.max(1L, (result.getRetryAfterMillis() + 999L) / 1000L);
            response.setHeader(HEADER_RETRY_AFTER, String.valueOf(retryAfterSec));
            response.setStatus(429);
            return false;
        }
        return true;
    }

    private RateLimiter obtainLimiter(String key) {
        RateLimiter existing = registry.get(key);
        if (existing != null) {
            return existing;
        }
        return registry.register(key, buildDefaultConfig());
    }

    private RateLimiterConfig buildDefaultConfig() {
        Algorithm algorithm;
        try {
            algorithm = Algorithm.valueOf(properties.getDefaultAlgorithm());
        } catch (IllegalArgumentException e) {
            log.warn("throttle4j: invalid defaultAlgorithm '{}', using TOKEN_BUCKET",
                    properties.getDefaultAlgorithm());
            algorithm = Algorithm.TOKEN_BUCKET;
        }
        long windowMillis = WindowParser.parseToMillis(properties.getDefaultWindow());
        RateLimiterConfig.Builder builder = RateLimiterConfig.builder()
                .algorithm(algorithm)
                .limit(properties.getDefaultLimit())
                .windowMillis(windowMillis);
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            long refill = properties.getDefaultRefillRate();
            if (refill <= 0L) {
                refill = Math.max(1L, properties.getDefaultLimit() * 1000L / Math.max(1L, windowMillis));
            }
            builder.refillRate(refill);
        }
        return builder.build();
    }

    /**
     * Apply standard rate-limit response headers.
     *
     * @param response HTTP response
     * @param result   limiter result
     * @param limit    configured limit
     */
    private void setRateLimitHeaders(HttpServletResponse response, RateLimitResult result, long limit) {
        response.setHeader(HEADER_LIMIT, String.valueOf(limit));
        response.setHeader(HEADER_REMAINING, String.valueOf(result.getRemaining()));
        response.setHeader(HEADER_RESET, String.valueOf(result.getResetAt()));
    }
}
