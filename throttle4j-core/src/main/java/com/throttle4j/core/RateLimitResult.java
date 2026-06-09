package com.throttle4j.core;

/**
 * Result returned from a rate limiter acquire attempt.
 */
public class RateLimitResult {

    private final boolean allowed;
    private final long remaining;
    private final long resetAt;
    private final long retryAfterMillis;

    /**
     * Create a new result.
     *
     * @param allowed          whether the request is allowed
     * @param remaining        remaining quota in the current window
     * @param resetAt          epoch milliseconds when the window resets
     * @param retryAfterMillis suggested retry-after duration in millis (0 when allowed)
     */
    public RateLimitResult(boolean allowed, long remaining, long resetAt, long retryAfterMillis) {
        this.allowed = allowed;
        this.remaining = Math.max(0L, remaining);
        this.resetAt = resetAt;
        this.retryAfterMillis = Math.max(0L, retryAfterMillis);
    }

    /**
     * Build an "allowed" result.
     */
    public static RateLimitResult allowed(long remaining, long resetAt) {
        return new RateLimitResult(true, remaining, resetAt, 0L);
    }

    /**
     * Build a "rejected" result.
     */
    public static RateLimitResult rejected(long remaining, long resetAt, long retryAfterMillis) {
        return new RateLimitResult(false, remaining, resetAt, retryAfterMillis);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemaining() {
        return remaining;
    }

    public long getResetAt() {
        return resetAt;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }

    @Override
    public String toString() {
        return "RateLimitResult{" +
                "allowed=" + allowed +
                ", remaining=" + remaining +
                ", resetAt=" + resetAt +
                ", retryAfterMillis=" + retryAfterMillis +
                '}';
    }
}
