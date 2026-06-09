package com.throttle4j.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for throttle4j. All properties live under the
 * {@code throttle4j} prefix (e.g. {@code throttle4j.enabled=true}).
 */
@ConfigurationProperties(prefix = "throttle4j")
public class Throttle4jProperties {

    /** Master switch. When {@code false}, no beans are registered. */
    private boolean enabled = true;

    /** Default algorithm used by global web interceptor and {@code @RateLimit} fallbacks. */
    private String defaultAlgorithm = "TOKEN_BUCKET";

    /** Default permit count for the global web interceptor. */
    private long defaultLimit = 100;

    /** Default window string ({@code "1s"}, {@code "1m"}, ...). */
    private String defaultWindow = "1m";

    /** Default token-bucket refill rate (tokens per second). */
    private long defaultRefillRate = 10;

    /** Backing store implementation. */
    private StoreType storeType = StoreType.MEMORY;

    /** Web layer configuration. */
    private Web web = new Web();

    /** Redis configuration (used only when {@link #storeType} is {@link StoreType#REDIS}). */
    private RedisProperties redis = new RedisProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultAlgorithm() {
        return defaultAlgorithm;
    }

    public void setDefaultAlgorithm(String defaultAlgorithm) {
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public long getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(long defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public String getDefaultWindow() {
        return defaultWindow;
    }

    public void setDefaultWindow(String defaultWindow) {
        this.defaultWindow = defaultWindow;
    }

    public long getDefaultRefillRate() {
        return defaultRefillRate;
    }

    public void setDefaultRefillRate(long defaultRefillRate) {
        this.defaultRefillRate = defaultRefillRate;
    }

    public StoreType getStoreType() {
        return storeType;
    }

    public void setStoreType(StoreType storeType) {
        this.storeType = storeType;
    }

    public Web getWeb() {
        return web;
    }

    public void setWeb(Web web) {
        this.web = web;
    }

    public RedisProperties getRedis() {
        return redis;
    }

    public void setRedis(RedisProperties redis) {
        this.redis = redis;
    }

    /**
     * Backing store types.
     */
    public enum StoreType {
        /** In-process {@link com.throttle4j.store.InMemoryStore}. */
        MEMORY,
        /** Redis-backed store (requires {@code throttle4j-redis} on the classpath). */
        REDIS
    }

    /**
     * Web layer settings (interceptor + headers).
     */
    public static class Web {

        /** Enable global URL-path web interceptor. Defaults to {@code false}. */
        private boolean enabled = false;

        /** Path patterns to include. */
        private String[] includePatterns = new String[]{"/**"};

        /** Path patterns to exclude. */
        private String[] excludePatterns = new String[0];

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String[] getIncludePatterns() {
            return includePatterns;
        }

        public void setIncludePatterns(String[] includePatterns) {
            this.includePatterns = includePatterns;
        }

        public String[] getExcludePatterns() {
            return excludePatterns;
        }

        public void setExcludePatterns(String[] excludePatterns) {
            this.excludePatterns = excludePatterns;
        }
    }

    /**
     * Redis connection settings used when {@link #getStoreType()} is
     * {@link StoreType#REDIS}.
     */
    public static class RedisProperties {

        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private String keyPrefix = "throttle4j:";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
