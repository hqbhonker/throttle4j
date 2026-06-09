package com.throttle4j.algorithm;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiter;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.InMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowRateLimiterTest {

    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore(60_000L, 60_000L);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private RateLimiter newLimiter(long limit, long windowMillis) {
        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(limit)
                .windowMillis(windowMillis)
                .build();
        return new FixedWindowRateLimiter(cfg, store);
    }

    @Test
    void allowsUpToLimitThenRejects() {
        RateLimiter limiter = newLimiter(5, 1000L);
        for (int i = 0; i < 5; i++) {
            RateLimitResult r = limiter.tryAcquire("u1");
            assertTrue(r.isAllowed(), "request " + i + " should be allowed");
            assertEquals(4 - i, r.getRemaining());
        }
        RateLimitResult sixth = limiter.tryAcquire("u1");
        assertFalse(sixth.isAllowed());
        assertTrue(sixth.getRetryAfterMillis() > 0L);
    }

    @Test
    void resetsAfterWindow() throws InterruptedException {
        RateLimiter limiter = newLimiter(2, 200L);
        assertTrue(limiter.tryAcquire("k").isAllowed());
        assertTrue(limiter.tryAcquire("k").isAllowed());
        assertFalse(limiter.tryAcquire("k").isAllowed());
        Thread.sleep(250L);
        assertTrue(limiter.tryAcquire("k").isAllowed(),
                "should be allowed after window resets");
    }

    @Test
    void multiplePermitsConsumeQuota() {
        RateLimiter limiter = newLimiter(10, 1000L);
        RateLimitResult r = limiter.tryAcquire("k", 3);
        assertTrue(r.isAllowed());
        assertEquals(7, r.getRemaining());
        RateLimitResult r2 = limiter.tryAcquire("k", 8);
        assertFalse(r2.isAllowed());
        assertEquals(7, r2.getRemaining());
    }

    @Test
    void rejectsInvalidPermits() {
        RateLimiter limiter = newLimiter(5, 1000L);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire("k", 0));
    }

    @Test
    void concurrentAcquireDoesNotExceedLimit() throws InterruptedException {
        long limit = 50L;
        RateLimiter limiter = newLimiter(limit, 5_000L);
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (limiter.tryAcquire("shared").isAllowed()) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(limit, allowed.get(), "concurrent allowed must equal limit");
    }
}
