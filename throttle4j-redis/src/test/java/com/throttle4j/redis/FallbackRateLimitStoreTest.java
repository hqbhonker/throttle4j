package com.throttle4j.redis;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FallbackRateLimitStore}.
 */
@ExtendWith(MockitoExtension.class)
class FallbackRateLimitStoreTest {

    @Mock
    private RateLimitStore primary;

    @Mock
    private RateLimitStore fallback;

    private FallbackRateLimitStore store;

    private RateLimiterConfig config;

    @BeforeEach
    void setUp() {
        store = new FallbackRateLimitStore(primary, fallback);
        config = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(10)
                .windowMillis(1000)
                .build();
    }

    @Test
    void primarySuccessShortCircuitsFallback() {
        RateLimitResult expected = RateLimitResult.allowed(9L, 1000L);
        when(primary.tryAcquire(anyString(), anyInt(), any(RateLimiterConfig.class)))
                .thenReturn(expected);

        RateLimitResult result = store.tryAcquire("k", 1, config);

        assertSame(expected, result);
        verify(fallback, never()).tryAcquire(anyString(), anyInt(), any());
        assertEquals(0L, store.getFallbackInvocations());
    }

    @Test
    void primaryFailureFallsBack() {
        when(primary.tryAcquire(anyString(), anyInt(), any(RateLimiterConfig.class)))
                .thenThrow(new RuntimeException("boom"));
        RateLimitResult fallbackResult = RateLimitResult.allowed(7L, 999L);
        when(fallback.tryAcquire(anyString(), anyInt(), any(RateLimiterConfig.class)))
                .thenReturn(fallbackResult);

        RateLimitResult result = store.tryAcquire("k", 1, config);

        assertSame(fallbackResult, result);
        verify(fallback, times(1)).tryAcquire(eq("k"), eq(1), eq(config));
        assertEquals(1L, store.getFallbackInvocations());
    }

    @Test
    void multiplePrimaryFailuresAreCounted() {
        when(primary.tryAcquire(anyString(), anyInt(), any(RateLimiterConfig.class)))
                .thenThrow(new RuntimeException("network down"));
        when(fallback.tryAcquire(anyString(), anyInt(), any(RateLimiterConfig.class)))
                .thenReturn(RateLimitResult.allowed(0L, 0L));

        for (int i = 0; i < 3; i++) {
            assertNotNull(store.tryAcquire("k" + i, 1, config));
        }
        assertEquals(3L, store.getFallbackInvocations());
    }

    @Test
    void resetIsBroadcastToBothStores() {
        store.reset("k");
        verify(primary).reset("k");
        verify(fallback).reset("k");
    }

    @Test
    void resetSwallowsPrimaryFailure() {
        doThrow(new RuntimeException("oops")).when(primary).reset(anyString());
        store.reset("k");
        verify(fallback).reset("k");
    }

    @Test
    void resetSwallowsFallbackFailure() {
        doThrow(new RuntimeException("oops")).when(fallback).reset(anyString());
        // Should not bubble out
        store.reset("k");
        verify(primary).reset("k");
    }

    @Test
    void nullPrimaryRejected() {
        assertThrows(NullPointerException.class,
                () -> new FallbackRateLimitStore(null, fallback));
    }

    @Test
    void nullFallbackRejected() {
        assertThrows(NullPointerException.class,
                () -> new FallbackRateLimitStore(primary, null));
    }
}
