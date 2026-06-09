package com.throttle4j.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateExceededExceptionTest {

    @Test
    void carriesKeyAndResult() {
        RateLimitResult r = RateLimitResult.rejected(0, 100L, 50L);
        RateExceededException ex = new RateExceededException("user:1", r);
        assertEquals("user:1", ex.getKey());
        assertSame(r, ex.getResult());
        assertTrue(ex.getMessage().contains("user:1"));
    }
}
