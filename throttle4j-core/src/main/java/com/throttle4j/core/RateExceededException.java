package com.throttle4j.core;

/**
 * Thrown when a request is rejected by a rate limiter.
 */
public class RateExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String key;
    private final RateLimitResult result;

    public RateExceededException(String key, RateLimitResult result) {
        super("Rate limit exceeded for key: " + key);
        this.key = key;
        this.result = result;
    }

    public RateExceededException(String key, RateLimitResult result, String message) {
        super(message);
        this.key = key;
        this.result = result;
    }

    public String getKey() {
        return key;
    }

    public RateLimitResult getResult() {
        return result;
    }
}
