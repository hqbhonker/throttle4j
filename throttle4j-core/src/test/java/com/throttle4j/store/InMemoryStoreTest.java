package com.throttle4j.store;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStoreTest {

    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        // very small idle and very long cleanup interval — drive cleanup manually
        store = new InMemoryStore(50L, 60_000L);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private RateLimiterConfig fixedWindow(long limit, long windowMillis) {
        return RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(limit)
                .windowMillis(windowMillis)
                .build();
    }

    @Test
    void basicAcquireAndCount() {
        RateLimiterConfig cfg = fixedWindow(3, 1000L);
        assertTrue(store.tryAcquire("k", 1, cfg).isAllowed());
        assertTrue(store.tryAcquire("k", 1, cfg).isAllowed());
        RateLimitResult r3 = store.tryAcquire("k", 1, cfg);
        assertTrue(r3.isAllowed());
        assertEquals(0, r3.getRemaining());
        assertFalse(store.tryAcquire("k", 1, cfg).isAllowed());
    }

    @Test
    void resetClearsStateForKey() {
        RateLimiterConfig cfg = fixedWindow(2, 1000L);
        assertTrue(store.tryAcquire("k", 1, cfg).isAllowed());
        assertTrue(store.tryAcquire("k", 1, cfg).isAllowed());
        assertFalse(store.tryAcquire("k", 1, cfg).isAllowed());
        store.reset("k");
        assertTrue(store.tryAcquire("k", 1, cfg).isAllowed());
    }

    @Test
    void keysAreIsolated() {
        RateLimiterConfig cfg = fixedWindow(2, 1000L);
        assertTrue(store.tryAcquire("a", 1, cfg).isAllowed());
        assertTrue(store.tryAcquire("a", 1, cfg).isAllowed());
        assertFalse(store.tryAcquire("a", 1, cfg).isAllowed());

        // 'b' should not be affected by 'a'
        assertTrue(store.tryAcquire("b", 1, cfg).isAllowed());
        assertTrue(store.tryAcquire("b", 1, cfg).isAllowed());
        assertFalse(store.tryAcquire("b", 1, cfg).isAllowed());
    }

    @Test
    void cleanupRemovesIdleKeys() throws InterruptedException {
        RateLimiterConfig cfg = fixedWindow(3, 1000L);
        store.tryAcquire("a", 1, cfg);
        store.tryAcquire("b", 1, cfg);
        assertEquals(2, store.size());

        Thread.sleep(80L); // exceed the 50ms idle TTL
        int removed = store.cleanup();
        assertEquals(2, removed);
        assertEquals(0, store.size());
    }

    @Test
    void cleanupKeepsActiveKeys() throws InterruptedException {
        RateLimiterConfig cfg = fixedWindow(3, 1000L);
        store.tryAcquire("a", 1, cfg);
        Thread.sleep(80L);
        // touch 'a' so it becomes fresh again
        store.tryAcquire("a", 1, cfg);
        store.cleanup();
        assertEquals(1, store.size());
    }

    @Test
    void closePreventsFurtherAcquire() {
        RateLimiterConfig cfg = fixedWindow(3, 1000L);
        store.tryAcquire("a", 1, cfg);
        store.close();
        assertThrows(IllegalStateException.class, () -> store.tryAcquire("a", 1, cfg));
    }
}
