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

class TokenBucketRateLimiterTest {

    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore(60_000L, 60_000L);
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private RateLimiter newLimiter(long capacity, long refillRate) {
        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .limit(capacity)
                .refillRate(refillRate)
                .build();
        return new TokenBucketRateLimiter(cfg, store);
    }

    @Test
    void burstAllowedUpToCapacity() {
        RateLimiter limiter = newLimiter(5, 1);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("u").isAllowed());
        }
        assertFalse(limiter.tryAcquire("u").isAllowed());
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        RateLimiter limiter = newLimiter(5, 10); // 10 tokens/sec
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("u").isAllowed());
        }
        assertFalse(limiter.tryAcquire("u").isAllowed());
        Thread.sleep(250L); // ~2.5 tokens refilled
        assertTrue(limiter.tryAcquire("u").isAllowed());
        assertTrue(limiter.tryAcquire("u").isAllowed());
    }

    @Test
    void multiplePermitsConsumeMultipleTokens() {
        RateLimiter limiter = newLimiter(10, 1);
        RateLimitResult r = limiter.tryAcquire("k", 7);
        assertTrue(r.isAllowed());
        assertEquals(3, r.getRemaining());
        assertFalse(limiter.tryAcquire("k", 5).isAllowed());
    }

    @Test
    void retryAfterIsReportedWhenRejected() {
        RateLimiter limiter = newLimiter(2, 1); // 1 token/sec
        assertTrue(limiter.tryAcquire("k").isAllowed());
        assertTrue(limiter.tryAcquire("k").isAllowed());
        RateLimitResult r = limiter.tryAcquire("k");
        assertFalse(r.isAllowed());
        assertTrue(r.getRetryAfterMillis() > 0L);
    }

    @Test
    void concurrentAcquireDoesNotExceedCapacity() throws InterruptedException {
        long capacity = 50L;
        // very low refill so refill during the burst is negligible
        RateLimiter limiter = newLimiter(capacity, 1);
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
        // allow some slack from refill during the burst (a few extra tokens)
        assertTrue(allowed.get() >= capacity, "allowed=" + allowed.get());
        assertTrue(allowed.get() <= capacity + 5, "allowed=" + allowed.get());
    }
}
