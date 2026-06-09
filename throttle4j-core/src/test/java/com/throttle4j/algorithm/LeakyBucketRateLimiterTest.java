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

class LeakyBucketRateLimiterTest {

    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore(60_000L, 60_000L);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private RateLimiter newLimiter(long capacity, long windowMillis) {
        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.LEAKY_BUCKET)
                .limit(capacity)
                .windowMillis(windowMillis)
                .build();
        return new LeakyBucketRateLimiter(cfg, store);
    }

    @Test
    void fillsToCapacityThenRejects() {
        RateLimiter limiter = newLimiter(5, 1000L);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("u").isAllowed());
        }
        assertFalse(limiter.tryAcquire("u").isAllowed());
    }

    @Test
    void leaksOverTime() throws InterruptedException {
        RateLimiter limiter = newLimiter(5, 500L); // 10 leak/sec
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("u").isAllowed());
        }
        assertFalse(limiter.tryAcquire("u").isAllowed());
        Thread.sleep(300L); // ~3 leaked
        assertTrue(limiter.tryAcquire("u").isAllowed());
    }

    @Test
    void multiplePermits() {
        RateLimiter limiter = newLimiter(10, 1000L);
        RateLimitResult r = limiter.tryAcquire("k", 6);
        assertTrue(r.isAllowed());
        assertTrue(limiter.tryAcquire("k", 4).isAllowed());
        assertFalse(limiter.tryAcquire("k", 1).isAllowed());
    }

    @Test
    void retryAfterReportedWhenRejected() {
        RateLimiter limiter = newLimiter(2, 1000L);
        assertTrue(limiter.tryAcquire("k").isAllowed());
        assertTrue(limiter.tryAcquire("k").isAllowed());
        RateLimitResult r = limiter.tryAcquire("k");
        assertFalse(r.isAllowed());
        assertTrue(r.getRetryAfterMillis() > 0L);
    }

    @Test
    void concurrentAcquireDoesNotExceedCapacity() throws InterruptedException {
        long capacity = 50L;
        // long window so leak during the burst is negligible
        RateLimiter limiter = newLimiter(capacity, 60_000L);
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
        assertTrue(allowed.get() >= capacity - 1, "allowed=" + allowed.get());
        assertTrue(allowed.get() <= capacity + 1, "allowed=" + allowed.get());
    }
}
