package com.throttle4j.spring.aop;

import com.throttle4j.algorithm.DefaultRateLimiterFactory;
import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateExceededException;
import com.throttle4j.core.RateLimiterRegistry;
import com.throttle4j.spring.annotation.RateLimit;
import com.throttle4j.spring.autoconfigure.Throttle4jProperties;
import com.throttle4j.store.InMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@link RateLimitAspect} integration without bootstrapping a
 * full Spring Boot application context.
 */
class RateLimitAspectTest {

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void allowsUnderLimit() {
        LimitedService service = (LimitedService) context.getBean("limitedService");
        assertThat(service.greet("alice")).isEqualTo("hello alice");
    }

    @Test
    void rejectsAfterLimit() {
        LimitedService service = (LimitedService) context.getBean("limitedService");
        assertThat(service.limited("u1")).isEqualTo("ok-u1");
        assertThat(service.limited("u1")).isEqualTo("ok-u1");
        assertThatThrownBy(() -> service.limited("u1"))
                .isInstanceOf(RateExceededException.class);
    }

    @Test
    void invokesFallback() {
        FallbackService service = (FallbackService) context.getBean("fallbackService");
        assertThat(service.work("k1")).isEqualTo("done-k1");
        assertThat(service.work("k1")).isEqualTo("done-k1");
        assertThat(service.work("k1")).isEqualTo("fallback-k1");
    }

    @Test
    void differentKeysDoNotShareLimit() {
        LimitedService service = (LimitedService) context.getBean("limitedService");
        assertThat(service.limited("alpha")).isEqualTo("ok-alpha");
        assertThat(service.limited("beta")).isEqualTo("ok-beta");
        assertThat(service.limited("gamma")).isEqualTo("ok-gamma");
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {

        @Bean
        public Throttle4jProperties throttle4jProperties() {
            return new Throttle4jProperties();
        }

        @Bean
        public RateLimiterRegistry rateLimiterRegistry() {
            return new RateLimiterRegistry(new DefaultRateLimiterFactory(new InMemoryStore()));
        }

        @Bean
        public RateLimitAspect rateLimitAspect(RateLimiterRegistry registry, Throttle4jProperties props) {
            return new RateLimitAspect(registry, props);
        }

        @Bean
        public LimitedService limitedService() {
            return new LimitedService();
        }

        @Bean
        public FallbackService fallbackService() {
            return new FallbackService();
        }
    }

    public static class LimitedService {

        @RateLimit(limit = 1000, window = "1m", algorithm = Algorithm.FIXED_WINDOW)
        public String greet(String name) {
            return "hello " + name;
        }

        @RateLimit(key = "'limited-test:' + #user", limit = 2, window = "1m",
                algorithm = Algorithm.FIXED_WINDOW)
        public String limited(String user) {
            return "ok-" + user;
        }
    }

    public static class FallbackService {

        @RateLimit(key = "'fallback-test:' + #id", limit = 2, window = "1m",
                algorithm = Algorithm.FIXED_WINDOW, fallbackMethod = "fallback")
        public String work(String id) {
            return "done-" + id;
        }

        public String fallback(String id) {
            return "fallback-" + id;
        }
    }
}
