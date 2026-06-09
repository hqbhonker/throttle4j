package com.throttle4j.store;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process {@link RateLimitStore} backed by {@link ConcurrentHashMap} maps,
 * one per supported algorithm.
 *
 * <p>A daemon scheduled executor periodically removes idle keys that have not
 * been accessed for {@code idleMillis}.</p>
 */
public class InMemoryStore implements RateLimitStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(InMemoryStore.class);

    /** Default idle TTL for a key after last access (5 minutes). */
    public static final long DEFAULT_IDLE_MILLIS = TimeUnit.MINUTES.toMillis(5);
    /** Default cleanup interval (60 seconds). */
    public static final long DEFAULT_CLEANUP_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(60);

    private final ConcurrentHashMap<String, FixedWindowState> fixedWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlidingWindowState> slidingWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucketState> tokenBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LeakyBucketState> leakyBuckets = new ConcurrentHashMap<>();

    private final long idleMillis;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Use defaults: 5min idle TTL, 60s cleanup interval. */
    public InMemoryStore() {
        this(DEFAULT_IDLE_MILLIS, DEFAULT_CLEANUP_INTERVAL_MILLIS);
    }

    /**
     * @param idleMillis              idle TTL after which a key is eligible for removal
     * @param cleanupIntervalMillis   how often the cleanup task runs
     */
    public InMemoryStore(long idleMillis, long cleanupIntervalMillis) {
        if (idleMillis <= 0) {
            throw new IllegalArgumentException("idleMillis must be > 0");
        }
        if (cleanupIntervalMillis <= 0) {
            throw new IllegalArgumentException("cleanupIntervalMillis must be > 0");
        }
        this.idleMillis = idleMillis;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "throttle4j-inmemory-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanup, cleanupIntervalMillis, cleanupIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public RateLimitResult tryAcquire(String key, int permits, RateLimiterConfig config) {
        if (closed.get()) {
            throw new IllegalStateException("InMemoryStore is closed");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1");
        }
        long now = System.currentTimeMillis();
        Algorithm algo = config.getAlgorithm();
        switch (algo) {
            case FIXED_WINDOW:
                return acquireFixedWindow(key, permits, config, now);
            case SLIDING_WINDOW:
                return acquireSlidingWindow(key, permits, config, now);
            case TOKEN_BUCKET:
                return acquireTokenBucket(key, permits, config, now);
            case LEAKY_BUCKET:
                return acquireLeakyBucket(key, permits, config, now);
            default:
                throw new IllegalArgumentException("Unsupported algorithm: " + algo);
        }
    }

    @Override
    public void reset(String key) {
        fixedWindows.remove(key);
        slidingWindows.remove(key);
        tokenBuckets.remove(key);
        leakyBuckets.remove(key);
    }

    /**
     * Run cleanup immediately. Removes keys whose last access time is older than
     * {@code now - idleMillis}.
     *
     * @return total number of entries removed
     */
    public int cleanup() {
        long threshold = System.currentTimeMillis() - idleMillis;
        int removed = 0;
        removed += removeIdle(fixedWindows, threshold);
        removed += removeIdle(slidingWindows, threshold);
        removed += removeIdle(tokenBuckets, threshold);
        removed += removeIdle(leakyBuckets, threshold);
        if (removed > 0 && log.isDebugEnabled()) {
            log.debug("InMemoryStore cleanup removed {} idle keys", removed);
        }
        return removed;
    }

    private static <T extends BaseState> int removeIdle(Map<String, T> map, long threshold) {
        int removed = 0;
        Iterator<Map.Entry<String, T>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, T> e = it.next();
            if (e.getValue().lastAccessAt < threshold) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Total number of stored keys across all algorithms. */
    public int size() {
        return fixedWindows.size() + slidingWindows.size()
                + tokenBuckets.size() + leakyBuckets.size();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanupExecutor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- algorithms

    private RateLimitResult acquireFixedWindow(String key, int permits,
                                               RateLimiterConfig config, long now) {
        long windowMillis = config.getWindowMillis();
        long limit = config.getLimit();
        FixedWindowState st = fixedWindows.computeIfAbsent(key, k -> new FixedWindowState(now));
        synchronized (st) {
            if (now - st.windowStart >= windowMillis) {
                st.windowStart = now;
                st.count = 0L;
            }
            st.lastAccessAt = now;
            long resetAt = st.windowStart + windowMillis;
            if (st.count + permits > limit) {
                long remaining = Math.max(0L, limit - st.count);
                long retryAfter = Math.max(0L, resetAt - now);
                return RateLimitResult.rejected(remaining, resetAt, retryAfter);
            }
            st.count += permits;
            return RateLimitResult.allowed(limit - st.count, resetAt);
        }
    }

    private RateLimitResult acquireSlidingWindow(String key, int permits,
                                                 RateLimiterConfig config, long now) {
        long windowMillis = config.getWindowMillis();
        long limit = config.getLimit();
        SlidingWindowState st = slidingWindows.computeIfAbsent(key,
                k -> new SlidingWindowState(SlidingWindowState.SLOTS, now));
        synchronized (st) {
            st.lastAccessAt = now;
            long slotSize = Math.max(1L, windowMillis / SlidingWindowState.SLOTS);
            long currentSlotId = now / slotSize;
            long firstValid = currentSlotId - SlidingWindowState.SLOTS + 1;
            // Evict slots out of window
            long total = 0L;
            for (int i = 0; i < SlidingWindowState.SLOTS; i++) {
                if (st.slotIds[i] < firstValid) {
                    st.slotIds[i] = -1L;
                    st.counts[i] = 0L;
                } else {
                    total += st.counts[i];
                }
            }
            long resetAt = (currentSlotId + 1) * slotSize;
            if (total + permits > limit) {
                long remaining = Math.max(0L, limit - total);
                // retry after the oldest slot rolls off
                long oldestSlot = firstValid;
                long retryAfter = Math.max(0L, (oldestSlot + 1) * slotSize - now);
                if (retryAfter == 0L) {
                    retryAfter = slotSize;
                }
                return RateLimitResult.rejected(remaining, resetAt, retryAfter);
            }
            int idx = (int) Math.floorMod(currentSlotId, (long) SlidingWindowState.SLOTS);
            if (st.slotIds[idx] != currentSlotId) {
                st.slotIds[idx] = currentSlotId;
                st.counts[idx] = 0L;
            }
            st.counts[idx] += permits;
            return RateLimitResult.allowed(limit - (total + permits), resetAt);
        }
    }

    private RateLimitResult acquireTokenBucket(String key, int permits,
                                               RateLimiterConfig config, long now) {
        long limit = config.getLimit();
        long refillRate = config.getRefillRate();
        TokenBucketState st = tokenBuckets.computeIfAbsent(key,
                k -> new TokenBucketState(limit, now));
        synchronized (st) {
            double elapsedSec = Math.max(0L, now - st.lastRefillMillis) / 1000.0;
            double refilled = elapsedSec * refillRate;
            st.tokens = Math.min((double) limit, st.tokens + refilled);
            st.lastRefillMillis = now;
            st.lastAccessAt = now;
            long resetAt = now + config.getWindowMillis();
            if (st.tokens >= permits) {
                st.tokens -= permits;
                return RateLimitResult.allowed((long) Math.floor(st.tokens), resetAt);
            }
            double deficit = permits - st.tokens;
            long retryAfter = (long) Math.ceil(deficit / refillRate * 1000.0);
            return RateLimitResult.rejected((long) Math.floor(st.tokens),
                    now + retryAfter, retryAfter);
        }
    }

    private RateLimitResult acquireLeakyBucket(String key, int permits,
                                               RateLimiterConfig config, long now) {
        long limit = config.getLimit();
        long windowMillis = config.getWindowMillis();
        double leakPerMs = (double) limit / (double) windowMillis;
        LeakyBucketState st = leakyBuckets.computeIfAbsent(key,
                k -> new LeakyBucketState(now));
        synchronized (st) {
            long elapsed = Math.max(0L, now - st.lastLeakMillis);
            double leaked = elapsed * leakPerMs;
            st.level = Math.max(0.0, st.level - leaked);
            st.lastLeakMillis = now;
            st.lastAccessAt = now;
            long resetAt = now + (long) Math.ceil(st.level / leakPerMs);
            if (st.level + permits <= limit) {
                st.level += permits;
                long remaining = Math.max(0L, (long) Math.floor(limit - st.level));
                long resetAfter = (long) Math.ceil(st.level / leakPerMs);
                return RateLimitResult.allowed(remaining, now + resetAfter);
            }
            double overflow = (st.level + permits) - limit;
            long retryAfter = (long) Math.ceil(overflow / leakPerMs);
            long remaining = Math.max(0L, (long) Math.floor(limit - st.level));
            return RateLimitResult.rejected(remaining, resetAt, retryAfter);
        }
    }

    // ---------------------------------------------------------------- state

    abstract static class BaseState {
        volatile long lastAccessAt;
    }

    static final class FixedWindowState extends BaseState {
        long windowStart;
        long count;
        FixedWindowState(long now) {
            this.windowStart = now;
            this.count = 0L;
            this.lastAccessAt = now;
        }
    }

    static final class SlidingWindowState extends BaseState {
        static final int SLOTS = 10;
        final long[] slotIds;
        final long[] counts;
        SlidingWindowState(int slots, long now) {
            this.slotIds = new long[slots];
            this.counts = new long[slots];
            for (int i = 0; i < slots; i++) {
                this.slotIds[i] = -1L;
            }
            this.lastAccessAt = now;
        }
    }

    static final class TokenBucketState extends BaseState {
        double tokens;
        long lastRefillMillis;
        TokenBucketState(long initialTokens, long now) {
            this.tokens = initialTokens;
            this.lastRefillMillis = now;
            this.lastAccessAt = now;
        }
    }

    static final class LeakyBucketState extends BaseState {
        double level;
        long lastLeakMillis;
        LeakyBucketState(long now) {
            this.level = 0.0;
            this.lastLeakMillis = now;
            this.lastAccessAt = now;
        }
    }
}
