package com.throttle4j.core;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that manages named {@link RateLimiter} instances.
 *
 * <p>This registry is fully thread-safe; concurrent calls to
 * {@link #register(String, RateLimiterConfig)} for the same name will return
 * the same instance (first-write-wins semantics via
 * {@link ConcurrentHashMap#computeIfAbsent}).</p>
 */
public class RateLimiterRegistry {

    private final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final RateLimiterFactory factory;

    public RateLimiterRegistry(RateLimiterFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory must not be null");
    }

    /**
     * @param name limiter name
     * @return the registered limiter or {@code null} if absent
     */
    public RateLimiter get(String name) {
        return limiters.get(name);
    }

    /**
     * Register a limiter for the given name. If one already exists, the existing
     * instance is returned and the supplied config is ignored.
     *
     * @param name   logical limiter name
     * @param config configuration to materialize when absent
     * @return the registered limiter
     */
    public RateLimiter register(String name, RateLimiterConfig config) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(config, "config must not be null");
        return limiters.computeIfAbsent(name, n -> factory.create(config));
    }

    /**
     * Remove a registered limiter.
     *
     * @param name limiter name
     */
    public void remove(String name) {
        limiters.remove(name);
    }

    /**
     * @return the number of registered limiters
     */
    public int size() {
        return limiters.size();
    }
}
