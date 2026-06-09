package com.throttle4j.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitResultTest {

    @Test
    void allowedFactoryHasZeroRetryAfter() {
        RateLimitResult r = RateLimitResult.allowed(3, 1000L);
        assertTrue(r.isAllowed());
        assertEquals(3L, r.getRemaining());
        assertEquals(1000L, r.getResetAt());
        assertEquals(0L, r.getRetryAfterMillis());
    }

    @Test
    void rejectedFactoryReportsRetryAfter() {
        RateLimitResult r = RateLimitResult.rejected(0, 1000L, 500L);
        assertFalse(r.isAllowed());
        assertEquals(0L, r.getRemaining());
        assertEquals(500L, r.getRetryAfterMillis());
    }

    @Test
    void negativeRemainingClampedToZero() {
        RateLimitResult r = new RateLimitResult(false, -1L, 0L, -5L);
        assertEquals(0L, r.getRemaining());
        assertEquals(0L, r.getRetryAfterMillis());
    }
}
