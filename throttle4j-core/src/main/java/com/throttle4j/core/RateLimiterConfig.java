package com.throttle4j.core;

import java.util.Objects;

/**
 * Immutable configuration for a {@link RateLimiter}, built via {@link Builder}.
 */
public class RateLimiterConfig {

    private final Algorithm algorithm;
    private final long limit;
    private final long windowMillis;
    private final long refillRate;

    private RateLimiterConfig(Builder builder) {
        this.algorithm = builder.algorithm;
        this.limit = builder.limit;
        this.windowMillis = builder.windowMillis;
        this.refillRate = builder.refillRate;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public long getLimit() {
        return limit;
    }

    public long getWindowMillis() {
        return windowMillis;
    }

    public long getRefillRate() {
        return refillRate;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "RateLimiterConfig{" +
                "algorithm=" + algorithm +
                ", limit=" + limit +
                ", windowMillis=" + windowMillis +
                ", refillRate=" + refillRate +
                '}';
    }

    /**
     * Builder for {@link RateLimiterConfig}.
     */
    public static class Builder {
        private Algorithm algorithm;
        private long limit = -1L;
        private long windowMillis = -1L;
        private long refillRate = -1L;

        public Builder algorithm(Algorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder limit(long limit) {
            this.limit = limit;
            return this;
        }

        public Builder windowSeconds(long seconds) {
            this.windowMillis = seconds * 1000L;
            return this;
        }

        public Builder windowMillis(long millis) {
            this.windowMillis = millis;
            return this;
        }

        public Builder refillRate(long tokensPerSecond) {
            this.refillRate = tokensPerSecond;
            return this;
        }

        /**
         * Build and validate the configuration.
         *
         * @return a new immutable {@link RateLimiterConfig}
         */
        public RateLimiterConfig build() {
            Objects.requireNonNull(algorithm, "algorithm must not be null");
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be > 0");
            }
            if (algorithm == Algorithm.TOKEN_BUCKET) {
                if (refillRate <= 0) {
                    throw new IllegalArgumentException("refillRate must be > 0 for TOKEN_BUCKET");
                }
                if (windowMillis <= 0) {
                    // not strictly required for token bucket; default to 1s for resetAt reporting
                    windowMillis = 1000L;
                }
            } else {
                if (windowMillis <= 0) {
                    throw new IllegalArgumentException("windowMillis must be > 0");
                }
            }
            return new RateLimiterConfig(this);
        }
    }
}
