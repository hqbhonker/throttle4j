package com.throttle4j.redis;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Distributed {@link RateLimitStore} backed by Redis. The actual algorithm
 * logic lives in Lua scripts shipped with this module so that each request is
 * applied atomically on the Redis server.
 *
 * <p>Three Lua scripts under {@code scripts/} on the classpath are loaded once
 * at construction time:</p>
 *
 * <ul>
 *   <li>{@code fixed_window.lua}  — fixed window counter</li>
 *   <li>{@code sliding_window.lua} — sliding window via sorted set</li>
 *   <li>{@code token_bucket.lua}   — token bucket (also reused for leaky bucket)</li>
 * </ul>
 *
 * <p>Instances are thread-safe as long as the supplied {@link RedisCommands}
 * connection is thread-safe. Lettuce's stateful connections are thread-safe
 * by default, so a single instance can be safely shared.</p>
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    /** Default key prefix applied to every limiter key to avoid collisions. */
    public static final String DEFAULT_KEY_PREFIX = "throttle4j:";

    static final String FIXED_WINDOW_PATH = "scripts/fixed_window.lua";
    static final String SLIDING_WINDOW_PATH = "scripts/sliding_window.lua";
    static final String TOKEN_BUCKET_PATH = "scripts/token_bucket.lua";

    private final RedisCommands<String, String> commands;
    private final String keyPrefix;
    private final String fixedWindowScript;
    private final String slidingWindowScript;
    private final String tokenBucketScript;

    /**
     * Build a store using the default key prefix ({@value #DEFAULT_KEY_PREFIX}).
     *
     * @param commands a synchronous Lettuce command interface
     */
    public RedisRateLimitStore(RedisCommands<String, String> commands) {
        this(commands, DEFAULT_KEY_PREFIX);
    }

    /**
     * Build a store with a custom key prefix.
     *
     * @param commands  a synchronous Lettuce command interface
     * @param keyPrefix prefix for every Redis key (must not be {@code null})
     */
    public RedisRateLimitStore(RedisCommands<String, String> commands, String keyPrefix) {
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        this.fixedWindowScript = loadScript(FIXED_WINDOW_PATH);
        this.slidingWindowScript = loadScript(SLIDING_WINDOW_PATH);
        this.tokenBucketScript = loadScript(TOKEN_BUCKET_PATH);
    }

    @Override
    public RateLimitResult tryAcquire(String key, int permits, RateLimiterConfig config) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1");
        }
        Objects.requireNonNull(config, "config must not be null");

        String prefixedKey = keyPrefix + key;
        Algorithm algo = config.getAlgorithm();
        switch (algo) {
            case FIXED_WINDOW:
                return runFixedWindow(prefixedKey, permits, config);
            case SLIDING_WINDOW:
                return runSlidingWindow(prefixedKey, permits, config);
            case TOKEN_BUCKET:
                return runTokenBucket(prefixedKey, permits, config,
                        config.getLimit(), (double) config.getRefillRate());
            case LEAKY_BUCKET:
                // Leaky bucket is modelled as a token bucket whose refill rate
                // equals the configured leak rate (limit / windowSeconds).
                double leakRate = (double) config.getLimit() * 1000.0
                        / (double) config.getWindowMillis();
                return runTokenBucket(prefixedKey, permits, config,
                        config.getLimit(), leakRate);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algo);
        }
    }

    @Override
    public void reset(String key) {
        if (key == null) {
            return;
        }
        commands.del(keyPrefix + key);
    }

    // ------------------------------------------------------------- algorithm dispatch

    private RateLimitResult runFixedWindow(String prefixedKey, int permits,
                                           RateLimiterConfig config) {
        long now = System.currentTimeMillis();
        String[] keys = {prefixedKey};
        String[] args = {
                String.valueOf(config.getLimit()),
                String.valueOf(config.getWindowMillis()),
                String.valueOf(permits)
        };
        List<Object> result = evalMulti(fixedWindowScript, keys, args);
        long allowed = longAt(result, 0);
        long remaining = longAt(result, 1);
        long ttl = longAt(result, 2);
        long resetAt = now + Math.max(0L, ttl);
        if (allowed == 1L) {
            return RateLimitResult.allowed(remaining, resetAt);
        }
        return RateLimitResult.rejected(remaining, resetAt, Math.max(0L, ttl));
    }

    private RateLimitResult runSlidingWindow(String prefixedKey, int permits,
                                             RateLimiterConfig config) {
        long now = System.currentTimeMillis();
        String uniqueId = now + "-" + UUID.randomUUID();
        String[] keys = {prefixedKey};
        String[] args = {
                String.valueOf(config.getLimit()),
                String.valueOf(config.getWindowMillis()),
                String.valueOf(permits),
                String.valueOf(now),
                uniqueId
        };
        List<Object> result = evalMulti(slidingWindowScript, keys, args);
        long allowed = longAt(result, 0);
        long remaining = longAt(result, 1);
        long resetAt = longAt(result, 2);
        if (allowed == 1L) {
            return RateLimitResult.allowed(remaining, resetAt);
        }
        long retryAfter = Math.max(0L, resetAt - now);
        return RateLimitResult.rejected(remaining, resetAt, retryAfter);
    }

    private RateLimitResult runTokenBucket(String prefixedKey, int permits,
                                           RateLimiterConfig config,
                                           long capacity, double refillRate) {
        long now = System.currentTimeMillis();
        String[] keys = {prefixedKey};
        String[] args = {
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(permits),
                String.valueOf(now)
        };
        List<Object> result = evalMulti(tokenBucketScript, keys, args);
        long allowed = longAt(result, 0);
        long remaining = longAt(result, 1);
        long resetAt = now + Math.max(1L, config.getWindowMillis());
        if (allowed == 1L) {
            return RateLimitResult.allowed(remaining, resetAt);
        }
        long retryAfter;
        if (refillRate > 0.0) {
            double deficit = Math.max(1.0, permits - remaining);
            retryAfter = (long) Math.ceil(deficit / refillRate * 1000.0);
        } else {
            retryAfter = config.getWindowMillis();
        }
        return RateLimitResult.rejected(remaining, now + retryAfter, retryAfter);
    }

    // ------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private List<Object> evalMulti(String script, String[] keys, String[] args) {
        Object raw = commands.eval(script, ScriptOutputType.MULTI, keys, args);
        if (!(raw instanceof List)) {
            throw new IllegalStateException(
                    "Lua script returned unexpected value: " + raw);
        }
        return (List<Object>) raw;
    }

    private static long longAt(List<Object> list, int index) {
        if (list == null || list.size() <= index) {
            return 0L;
        }
        Object value = list.get(index);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * Load a classpath-relative Lua script as a UTF-8 string.
     *
     * @param path classpath location of the script
     * @return the script content
     * @throws IllegalStateException if the resource is missing
     */
    static String loadScript(String path) {
        ClassLoader cl = RedisRateLimitStore.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Lua script not found on classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                String content = sb.toString();
                if (content.trim().isEmpty()) {
                    throw new IllegalStateException("Lua script is empty: " + path);
                }
                if (log.isDebugEnabled()) {
                    log.debug("Loaded Lua script {} ({} bytes)", path, content.length());
                }
                return content;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load Lua script: " + path, e);
        }
    }

    /** @return the configured Redis key prefix. */
    public String getKeyPrefix() {
        return keyPrefix;
    }
}
