package com.throttle4j.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterConfigTest {

    @Test
    void buildsValidFixedWindowConfig() {
        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(100)
                .windowSeconds(2)
                .build();
        assertEquals(Algorithm.FIXED_WINDOW, cfg.getAlgorithm());
        assertEquals(100L, cfg.getLimit());
        assertEquals(2000L, cfg.getWindowMillis());
    }

    @Test
    void buildsValidTokenBucketConfig() {
        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .limit(10)
                .refillRate(5)
                .build();
        assertEquals(5L, cfg.getRefillRate());
        assertTrue(cfg.getWindowMillis() > 0);
    }

    @Test
    void rejectsMissingAlgorithm() {
        assertThrows(NullPointerException.class, () -> RateLimiterConfig.builder()
                .limit(10).windowMillis(1000L).build());
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(0)
                .windowMillis(1000L)
                .build());
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(10)
                .build());
    }

    @Test
    void rejectsTokenBucketWithoutRefillRate() {
        assertThrows(IllegalArgumentException.class, () -> RateLimiterConfig.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .limit(10)
                .build());
    }
}
