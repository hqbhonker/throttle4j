package com.throttle4j.redis;

import io.lettuce.core.api.sync.RedisCommands;

import java.util.Objects;

/**
 * Fluent builder for {@link RedisRateLimitStore}.
 *
 * <pre>{@code
 * RedisRateLimitStore store = new RedisRateLimitStoreBuilder()
 *         .commands(connection.sync())
 *         .keyPrefix("myapp:rl:")
 *         .build();
 * }</pre>
 *
 * <p>Builder instances are <em>not</em> thread-safe; configure on a single
 * thread and call {@link #build()} once. The returned store is thread-safe.</p>
 */
public class RedisRateLimitStoreBuilder {

    private RedisCommands<String, String> commands;
    private String keyPrefix = RedisRateLimitStore.DEFAULT_KEY_PREFIX;

    /**
     * Set the synchronous Lettuce command interface to use.
     *
     * @param commands non-null commands
     * @return this builder
     */
    public RedisRateLimitStoreBuilder commands(RedisCommands<String, String> commands) {
        this.commands = Objects.requireNonNull(commands, "commands must not be null");
        return this;
    }

    /**
     * Set a custom key prefix. Defaults to {@link RedisRateLimitStore#DEFAULT_KEY_PREFIX}.
     *
     * @param prefix prefix; {@code null} is treated as empty
     * @return this builder
     */
    public RedisRateLimitStoreBuilder keyPrefix(String prefix) {
        this.keyPrefix = prefix == null ? "" : prefix;
        return this;
    }

    /**
     * Build the configured {@link RedisRateLimitStore}.
     *
     * @return a new store instance
     * @throws IllegalStateException if {@code commands} was not set
     */
    public RedisRateLimitStore build() {
        if (commands == null) {
            throw new IllegalStateException(
                    "commands must be configured before calling build()");
        }
        return new RedisRateLimitStore(commands, keyPrefix);
    }
}
