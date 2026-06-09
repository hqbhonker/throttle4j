package com.throttle4j.redis;

import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.store.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link RateLimitStore} that delegates to a primary store (typically
 * {@link RedisRateLimitStore}) and transparently falls back to a secondary
 * store (typically an in-memory implementation) when the primary throws an
 * exception.
 *
 * <p>This guards an application against transient Redis outages — the
 * application keeps applying rate limiting (in-process and per-node)
 * instead of failing open or closed.</p>
 *
 * <p>The reset operation is broadcast to both stores so that local fallback
 * state is also cleared.</p>
 *
 * <p>Thread-safety follows that of the underlying stores.</p>
 */
public class FallbackRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(FallbackRateLimitStore.class);

    private final RateLimitStore primary;
    private final RateLimitStore fallback;
    private final AtomicLong fallbackInvocations = new AtomicLong();

    /**
     * @param primary  primary store (typically Redis-backed)
     * @param fallback fallback store invoked when {@code primary} throws
     */
    public FallbackRateLimitStore(RateLimitStore primary, RateLimitStore fallback) {
        this.primary = Objects.requireNonNull(primary, "primary must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    @Override
    public RateLimitResult tryAcquire(String key, int permits, RateLimiterConfig config) {
        try {
            return primary.tryAcquire(key, permits, config);
        } catch (Exception e) {
            fallbackInvocations.incrementAndGet();
            log.warn("Primary rate limit store unavailable, falling back. key={}, error={}",
                    key, e.toString());
            if (log.isDebugEnabled()) {
                log.debug("Primary store failure stack trace", e);
            }
            return fallback.tryAcquire(key, permits, config);
        }
    }

    @Override
    public void reset(String key) {
        try {
            primary.reset(key);
        } catch (Exception e) {
            log.warn("Failed to reset key {} on primary store: {}", key, e.toString());
        }
        try {
            fallback.reset(key);
        } catch (Exception e) {
            log.warn("Failed to reset key {} on fallback store: {}", key, e.toString());
        }
    }

    /** @return number of times the fallback store has been invoked due to primary failures. */
    public long getFallbackInvocations() {
        return fallbackInvocations.get();
    }
}
