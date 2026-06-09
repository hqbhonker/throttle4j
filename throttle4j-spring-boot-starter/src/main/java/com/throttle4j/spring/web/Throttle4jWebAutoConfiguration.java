package com.throttle4j.spring.web;

import com.throttle4j.core.RateLimiterRegistry;
import com.throttle4j.spring.autoconfigure.Throttle4jAutoConfiguration;
import com.throttle4j.spring.autoconfigure.Throttle4jProperties;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web auto-configuration for throttle4j.
 *
 * <p>Activated only for servlet web applications when
 * {@code throttle4j.web.enabled=true}. Registers a global
 * {@link RateLimitInterceptor} via a {@link WebMvcConfigurer}.</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "throttle4j.web", name = "enabled", havingValue = "true", matchIfMissing = false)
@AutoConfigureAfter(Throttle4jAutoConfiguration.class)
@EnableConfigurationProperties(Throttle4jProperties.class)
public class Throttle4jWebAutoConfiguration {

    /**
     * @param registry   shared limiter registry
     * @param properties throttle4j configuration
     * @return interceptor bean
     */
    @Bean
    public RateLimitInterceptor rateLimitInterceptor(RateLimiterRegistry registry,
                                                     Throttle4jProperties properties) {
        return new RateLimitInterceptor(registry, properties);
    }

    /**
     * @param interceptor interceptor bean
     * @param properties  throttle4j configuration
     * @return MVC configurer that registers the interceptor with the requested patterns
     */
    @Bean
    public WebMvcConfigurer throttle4jWebMvcConfigurer(RateLimitInterceptor interceptor,
                                                      Throttle4jProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                String[] include = properties.getWeb().getIncludePatterns();
                String[] exclude = properties.getWeb().getExcludePatterns();
                registry.addInterceptor(interceptor)
                        .addPathPatterns(include != null && include.length > 0 ? include : new String[]{"/**"})
                        .excludePathPatterns(exclude != null ? exclude : new String[0]);
            }
        };
    }
}
