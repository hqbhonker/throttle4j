package com.throttle4j.example;

import com.throttle4j.algorithm.DefaultRateLimiterFactory;
import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiter;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.core.RateLimiterFactory;
import com.throttle4j.store.InMemoryStore;

/**
 * Programmatic usage example for the throttle4j core API.
 *
 * <p>This example does not depend on Spring. It demonstrates:
 * <ol>
 *   <li>Creating an {@link InMemoryStore}.</li>
 *   <li>Building rate limiters via {@link DefaultRateLimiterFactory} for two
 *       different algorithms (token bucket and fixed window).</li>
 *   <li>Issuing requests and printing the {@link RateLimitResult} of each call.</li>
 *   <li>Observing rejections once the configured limit is exhausted.</li>
 *   <li>Watching the limiter recover after the window resets / tokens refill.</li>
 * </ol>
 */
public class BasicUsageExample {

    private BasicUsageExample() {
        // utility entry point
    }

    public static void main(String[] args) throws InterruptedException {
        // 1. Build a single in-memory store shared by every limiter.
        try (InMemoryStore store = new InMemoryStore()) {
            RateLimiterFactory factory = new DefaultRateLimiterFactory(store);

            runTokenBucketDemo(factory);
            System.out.println();
            runFixedWindowDemo(factory);
        }
    }

    /**
     * Demonstrates a token bucket limiter: capacity 5 tokens, refilled at 5
     * tokens per second. The first 5 requests pass instantly, the 6th request
     * is rejected, and after waiting ~300ms a fresh token is available again.
     */
    private static void runTokenBucketDemo(RateLimiterFactory factory) throws InterruptedException {
        System.out.println("==== Token Bucket demo (capacity=5, refill=5/s) ====");

        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .limit(5)
                .refillRate(5)
                .build();
        RateLimiter limiter = factory.create(cfg);
        String key = "demo-token-bucket";

        // Drain the bucket: the first 5 calls should be allowed, the 6th rejected.
        for (int i = 1; i <= 6; i++) {
            RateLimitResult r = limiter.tryAcquire(key);
            System.out.printf("  request #%d -> allowed=%-5s remaining=%-3d retryAfterMillis=%d%n",
                    i, r.isAllowed(), r.getRemaining(), r.getRetryAfterMillis());
        }

        // Wait long enough for at least one token to refill, then try again.
        System.out.println("  ... sleeping 300ms to let tokens refill ...");
        Thread.sleep(300L);
        RateLimitResult after = limiter.tryAcquire(key);
        System.out.printf("  request #7 -> allowed=%-5s remaining=%-3d retryAfterMillis=%d%n",
                after.isAllowed(), after.getRemaining(), after.getRetryAfterMillis());
    }

    /**
     * Demonstrates a fixed-window limiter: 3 permits per 1-second window.
     * The 4th call is rejected, and after the window rolls over the limiter
     * accepts new traffic again.
     */
    private static void runFixedWindowDemo(RateLimiterFactory factory) throws InterruptedException {
        System.out.println("==== Fixed Window demo (limit=3, window=1s) ====");

        RateLimiterConfig cfg = RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(3)
                .windowSeconds(1)
                .build();
        RateLimiter limiter = factory.create(cfg);
        String key = "demo-fixed-window";

        for (int i = 1; i <= 4; i++) {
            RateLimitResult r = limiter.tryAcquire(key);
            System.out.printf("  request #%d -> allowed=%-5s remaining=%-3d retryAfterMillis=%d%n",
                    i, r.isAllowed(), r.getRemaining(), r.getRetryAfterMillis());
        }

        System.out.println("  ... sleeping 1100ms to let the window reset ...");
        Thread.sleep(1100L);
        RateLimitResult after = limiter.tryAcquire(key);
        System.out.printf("  request after reset -> allowed=%-5s remaining=%-3d retryAfterMillis=%d%n",
                after.isAllowed(), after.getRemaining(), after.getRetryAfterMillis());
    }
}
