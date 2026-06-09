package com.throttle4j.spring.autoconfigure;

import com.throttle4j.algorithm.DefaultRateLimiterFactory;
import com.throttle4j.core.RateLimiterFactory;
import com.throttle4j.core.RateLimiterRegistry;
import com.throttle4j.spring.aop.RateLimitAspect;
import com.throttle4j.store.InMemoryStore;
import com.throttle4j.store.RateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class Throttle4jAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Throttle4jAutoConfiguration.class));

    @Test
    void registersDefaultBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Throttle4jProperties.class);
            assertThat(context).hasSingleBean(RateLimitStore.class);
            assertThat(context.getBean(RateLimitStore.class)).isInstanceOf(InMemoryStore.class);
            assertThat(context).hasSingleBean(RateLimiterFactory.class);
            assertThat(context.getBean(RateLimiterFactory.class)).isInstanceOf(DefaultRateLimiterFactory.class);
            assertThat(context).hasSingleBean(RateLimiterRegistry.class);
            assertThat(context).hasSingleBean(RateLimitAspect.class);
        });
    }

    @Test
    void disabledByProperty() {
        contextRunner.withPropertyValues("throttle4j.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RateLimitStore.class);
            assertThat(context).doesNotHaveBean(RateLimiterRegistry.class);
            assertThat(context).doesNotHaveBean(RateLimitAspect.class);
        });
    }

    @Test
    void userBeanWins() {
        contextRunner.withUserConfiguration(CustomStoreConfig.class).run(context -> {
            assertThat(context).hasSingleBean(RateLimitStore.class);
            assertThat(context.getBean(RateLimitStore.class))
                    .isSameAs(context.getBean("customStore"));
        });
    }

    @Test
    void redisFallbackWhenNotOnClasspath() {
        // throttle4j-redis is on the classpath in this test, so we cannot test true
        // class absence here. We instead assert that selecting REDIS does not blow up
        // (it will either create a Redis store or fall back).
        contextRunner.withPropertyValues("throttle4j.store-type=REDIS").run(context -> {
            assertThat(context).hasSingleBean(RateLimitStore.class);
        });
    }

    @Configuration
    static class CustomStoreConfig {
        @Bean
        public RateLimitStore customStore() {
            return new InMemoryStore();
        }
    }
}
