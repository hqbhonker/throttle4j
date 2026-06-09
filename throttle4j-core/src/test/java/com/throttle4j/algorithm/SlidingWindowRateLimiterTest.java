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

class SlidingWindowRateLimiterTest {

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
                .algorithm(Algorithm.SLIDING_WINDOW)
                .limit(limit)
                .windowMillis(windowMillis)
                .build();
        return new SlidingWindowRateLimiter(cfg, store);
    }

    @Test
    void allowsUpToLimitThenRejects() {
        RateLimiter limiter = newLimiter(5, 1000L);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("u").isAllowed());
        }
        assertFalse(limiter.tryAcquire("u").isAllowed());
    }

    @Test
    void slidesAfterFullWindow() throws InterruptedException {
        RateLimiter limiter = newLimiter(3, 500L);
        assertTrue(limiter.tryAcquire("u").isAllowed());
        assertTrue(limiter.tryAcquire("u").isAllowed());
        assertTrue(limiter.tryAcquire("u").isAllowed());
        assertFalse(limiter.tryAcquire("u").isAllowed());
        // wait full window so all slots roll off
        Thread.sleep(700L);
        assertTrue(limiter.tryAcquire("u").isAllowed(),
                "should be allowed after full window slide");
    }

    @Test
    void multiplePermits() {
        RateLimiter limiter = newLimiter(10, 1000L);
        RateLimitResult r = limiter.tryAcquire("k", 4);
        assertTrue(r.isAllowed());
        assertTrue(limiter.tryAcquire("k", 6).isAllowed());
        assertFalse(limiter.tryAcquire("k", 1).isAllowed());
    }

    @Test
    void remainingDecreasesMonotonically() {
        RateLimiter limiter = newLimiter(5, 1000L);
        long lastRemaining = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            RateLimitResult r = limiter.tryAcquire("k");
            assertTrue(r.isAllowed());
            assertTrue(r.getRemaining() <= lastRemaining);
            lastRemaining = r.getRemaining();
        }
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
        assertTrue(allowed.get() <= limit, "allowed=" + allowed.get());
    }
}
