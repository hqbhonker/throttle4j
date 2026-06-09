package com.throttle4j.redis;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the bundled Lua scripts ship on the classpath and can be
 * loaded as non-empty UTF-8 strings via the helper.
 */
class LuaScriptLoadTest {

    @Test
    void allScriptsExistOnClasspath() {
        ClassLoader cl = getClass().getClassLoader();
        assertNotNull(cl.getResource(RedisRateLimitStore.FIXED_WINDOW_PATH),
                "fixed_window.lua must exist on classpath");
        assertNotNull(cl.getResource(RedisRateLimitStore.SLIDING_WINDOW_PATH),
                "sliding_window.lua must exist on classpath");
        assertNotNull(cl.getResource(RedisRateLimitStore.TOKEN_BUCKET_PATH),
                "token_bucket.lua must exist on classpath");
    }

    @Test
    void allScriptsAreLoadedAsNonEmpty() {
        for (String path : new String[]{
                RedisRateLimitStore.FIXED_WINDOW_PATH,
                RedisRateLimitStore.SLIDING_WINDOW_PATH,
                RedisRateLimitStore.TOKEN_BUCKET_PATH}) {
            String script = RedisRateLimitStore.loadScript(path);
            assertNotNull(script, path + " must load");
            assertFalse(script.trim().isEmpty(), path + " must be non-empty");
        }
    }

    @Test
    void fixedWindowScriptContainsExpectedCommands() {
        String script = RedisRateLimitStore.loadScript(RedisRateLimitStore.FIXED_WINDOW_PATH);
        assertTrue(script.contains("INCRBY"), "must contain INCRBY");
        assertTrue(script.contains("PTTL") || script.contains("TTL"),
                "must read TTL of the key");
    }

    @Test
    void slidingWindowScriptUsesSortedSet() {
        String script = RedisRateLimitStore.loadScript(RedisRateLimitStore.SLIDING_WINDOW_PATH);
        assertTrue(script.contains("ZREMRANGEBYSCORE"));
        assertTrue(script.contains("ZADD"));
        assertTrue(script.contains("ZCARD"));
    }

    @Test
    void tokenBucketScriptManipulatesHash() {
        String script = RedisRateLimitStore.loadScript(RedisRateLimitStore.TOKEN_BUCKET_PATH);
        assertTrue(script.contains("HMGET"));
        assertTrue(script.contains("HSET") || script.contains("HMSET"));
    }

    @Test
    void missingScriptThrows() {
        assertThrows(IllegalStateException.class,
                () -> RedisRateLimitStore.loadScript("scripts/does_not_exist.lua"));
    }

    @Test
    void scriptStreamCanBeRead() throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(RedisRateLimitStore.FIXED_WINDOW_PATH)) {
            assertNotNull(in);
            assertTrue(in.read() >= 0, "script must contain at least one byte");
        }
    }
}
