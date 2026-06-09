package com.throttle4j.redis;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link RedisRateLimitStore} using Mockito to stub Lettuce.
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

    @Mock
    private RedisCommands<String, String> commands;

    private RedisRateLimitStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRateLimitStore(commands);
    }

    @Test
    void tryAcquireFixedWindowAllowed() {
        doReturn(Arrays.asList(1L, 5L, 1000L))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(10)
                .windowMillis(1000)
                .build();

        RateLimitResult r = store.tryAcquire("user-1", 1, config);

        assertTrue(r.isAllowed());
        assertEquals(5L, r.getRemaining());
        assertEquals(0L, r.getRetryAfterMillis());
    }

    @Test
    void tryAcquireFixedWindowRejected() {
        doReturn(Arrays.asList(0L, 0L, 800L))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(10)
                .windowMillis(1000)
                .build();

        RateLimitResult r = store.tryAcquire("user-1", 1, config);

        assertFalse(r.isAllowed());
        assertEquals(0L, r.getRemaining());
        assertEquals(800L, r.getRetryAfterMillis());
    }

    @Test
    void fixedWindowUsesFixedWindowScriptAndPrefixedKey() {
        doReturn(Arrays.asList(1L, 9L, 1000L))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(10)
                .windowMillis(1000)
                .build();

        store.tryAcquire("api", 1, config);

        ArgumentCaptor<String> scriptCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> keysCap = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<String[]> argsCap = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(scriptCap.capture(), eq(ScriptOutputType.MULTI),
                keysCap.capture(), argsCap.capture());

        assertTrue(scriptCap.getValue().contains("ZREMRANGEBYSCORE") == false,
                "fixed_window.lua should not contain ZREMRANGEBYSCORE");
        assertTrue(scriptCap.getValue().contains("INCRBY"),
                "fixed_window.lua should contain INCRBY");
        assertEquals(1, keysCap.getValue().length);
        assertEquals("throttle4j:api", keysCap.getValue()[0]);
        // limit, windowMillis, permits
        assertEquals(3, argsCap.getValue().length);
        assertEquals("10", argsCap.getValue()[0]);
        assertEquals("1000", argsCap.getValue()[1]);
        assertEquals("1", argsCap.getValue()[2]);
    }

    @Test
    void slidingWindowUsesSlidingWindowScript() {
        long resetAt = System.currentTimeMillis() + 5000;
        doReturn(Arrays.asList(1L, 4L, resetAt))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.SLIDING_WINDOW)
                .limit(5)
                .windowMillis(5000)
                .build();

        RateLimitResult r = store.tryAcquire("api", 1, config);

        ArgumentCaptor<String> scriptCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> argsCap = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(scriptCap.capture(), eq(ScriptOutputType.MULTI),
                any(String[].class), argsCap.capture());

        assertTrue(scriptCap.getValue().contains("ZREMRANGEBYSCORE"));
        assertTrue(scriptCap.getValue().contains("ZADD"));
        // limit, windowMillis, permits, now, uniqueId
        assertEquals(5, argsCap.getValue().length);
        assertTrue(r.isAllowed());
        assertEquals(4L, r.getRemaining());
        assertEquals(resetAt, r.getResetAt());
    }

    @Test
    void slidingWindowRejectedReportsRetryAfter() {
        long resetAt = System.currentTimeMillis() + 2000;
        doReturn(Arrays.asList(0L, 0L, resetAt))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.SLIDING_WINDOW)
                .limit(5)
                .windowMillis(5000)
                .build();

        RateLimitResult r = store.tryAcquire("api", 1, config);

        assertFalse(r.isAllowed());
        assertTrue(r.getRetryAfterMillis() >= 0);
        assertTrue(r.getRetryAfterMillis() <= 2000);
    }

    @Test
    void tokenBucketUsesTokenBucketScript() {
        doReturn(Arrays.asList(1L, 9L, 0L))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .limit(10)
                .refillRate(5)
                .build();

        RateLimitResult r = store.tryAcquire("api", 1, config);

        ArgumentCaptor<String> scriptCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String[]> argsCap = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(scriptCap.capture(), eq(ScriptOutputType.MULTI),
                any(String[].class), argsCap.capture());

        assertTrue(scriptCap.getValue().contains("HMGET")
                || scriptCap.getValue().contains("HSET"));
        // capacity, refillRate, permits, now
        assertEquals(4, argsCap.getValue().length);
        assertEquals("10", argsCap.getValue()[0]);
        assertEquals("5.0", argsCap.getValue()[1]);
        assertEquals("1", argsCap.getValue()[2]);
        assertTrue(r.isAllowed());
        assertEquals(9L, r.getRemaining());
    }

    @Test
    void tokenBucketRejectedHasRetryAfter() {
        doReturn(Arrays.asList(0L, 0L, 0L))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .limit(10)
                .refillRate(5)
                .build();

        RateLimitResult r = store.tryAcquire("api", 1, config);

        assertFalse(r.isAllowed());
        assertTrue(r.getRetryAfterMillis() > 0);
    }

    @Test
    void leakyBucketReusesTokenBucketScriptWithComputedRate() {
        doReturn(Arrays.asList(1L, 4L, 0L))
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.LEAKY_BUCKET)
                .limit(5)
                .windowMillis(1000)
                .build();

        RateLimitResult r = store.tryAcquire("api", 1, config);

        ArgumentCaptor<String[]> argsCap = ArgumentCaptor.forClass(String[].class);
        verify(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                any(String[].class), argsCap.capture());
        // limit=5, windowMillis=1000 -> rate = 5.0 tokens/sec
        assertEquals("5.0", argsCap.getValue()[1]);
        assertTrue(r.isAllowed());
    }

    @Test
    void resetCallsDelWithPrefixedKey() {
        store.reset("api");
        verify(commands, times(1)).del("throttle4j:api");
    }

    @Test
    void resetWithNullIsSilentlyIgnored() {
        store.reset(null);
        verify(commands, times(0)).del(any(String[].class));
    }

    @Test
    void customKeyPrefixIsApplied() {
        store = new RedisRateLimitStoreBuilder()
                .commands(commands)
                .keyPrefix("myapp:")
                .build();
        store.reset("foo");
        verify(commands).del("myapp:foo");
    }

    @Test
    void nullKeyThrows() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(1)
                .windowMillis(1000)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> store.tryAcquire(null, 1, config));
    }

    @Test
    void invalidPermitsThrows() {
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(1)
                .windowMillis(1000)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> store.tryAcquire("k", 0, config));
    }

    @Test
    void unexpectedEvalReturnTypeThrows() {
        doReturn("not a list")
                .when(commands).eval(anyString(), eq(ScriptOutputType.MULTI),
                        any(String[].class), any(String[].class));
        RateLimiterConfig config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(1)
                .windowMillis(1000)
                .build();
        assertThrows(IllegalStateException.class,
                () -> store.tryAcquire("k", 1, config));
    }
}
