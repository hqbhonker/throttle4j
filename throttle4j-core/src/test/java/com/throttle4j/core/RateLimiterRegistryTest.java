package com.throttle4j.core;

import com.throttle4j.algorithm.DefaultRateLimiterFactory;
import com.throttle4j.store.InMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterRegistryTest {

    private InMemoryStore store;
    private RateLimiterRegistry registry;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore(60_000L, 60_000L);
        registry = new RateLimiterRegistry(new DefaultRateLimiterFactory(store));
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private RateLimiterConfig fixed() {
        return RateLimiterConfig.builder()
                .algorithm(Algorithm.FIXED_WINDOW)
                .limit(10)
                .windowMillis(1000L)
                .build();
    }

    @Test
    void registerAndGet() {
        RateLimiter limiter = registry.register("api-1", fixed());
        assertNotNull(limiter);
        assertSame(limiter, registry.get("api-1"));
        assertEquals(1, registry.size());
    }

    @Test
    void registerReturnsExistingForSameName() {
        RateLimiter first = registry.register("a", fixed());
        RateLimiter second = registry.register("a", fixed());
        assertSame(first, second);
    }

    @Test
    void removeDeletesLimiter() {
        registry.register("a", fixed());
        registry.remove("a");
        assertNull(registry.get("a"));
        assertEquals(0, registry.size());
    }

    @Test
    void getReturnsNullWhenAbsent() {
        assertNull(registry.get("missing"));
    }

    @Test
    void concurrentRegisterReturnsSameInstance() throws InterruptedException {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<RateLimiter> seen = java.util.Collections.synchronizedSet(new HashSet<>());
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    seen.add(registry.register("shared", fixed()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(1, seen.size(), "all threads must observe the same instance");
        assertEquals(1, registry.size());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> registry.register(null, fixed()));
        assertThrows(NullPointerException.class, () -> registry.register("a", null));
        assertThrows(NullPointerException.class, () -> new RateLimiterRegistry(null));
    }
}
