package com.throttle4j.spring.annotation;

import com.throttle4j.core.Algorithm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative rate-limit annotation for Spring beans.
 *
 * <p>When applied to a method of a Spring-managed bean, an AOP advice intercepts
 * the call and delegates to a backing {@code RateLimiter}. If the request is
 * rejected, either a configured {@link #fallbackMethod()} is invoked or a
 * {@link com.throttle4j.core.RateExceededException} is thrown.</p>
 *
 * <p>The {@link #key()} attribute supports SpEL evaluated against the method
 * arguments. When empty, the limiter key defaults to
 * {@code <ClassName>.<methodName>}.</p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Limiter key. Supports SpEL (e.g. {@code "#userId"} or
     * {@code "'user:' + #userId"}).
     *
     * <p>If empty, the key falls back to {@code ClassName.methodName}.</p>
     *
     * @return the key expression
     */
    String key() default "";

    /**
     * Maximum number of requests permitted within the window.
     *
     * @return permit count
     */
    long limit() default 100;

    /**
     * Window duration. Accepts shorthand strings such as {@code "500ms"},
     * {@code "1s"}, {@code "30s"}, {@code "1m"}, {@code "1h"}.
     *
     * @return window duration string
     */
    String window() default "1m";

    /**
     * Algorithm to use. Defaults to {@link com.throttle4j.core.Algorithm#SLIDING_WINDOW}.
     *
     * @return algorithm
     */
    Algorithm algorithm() default Algorithm.SLIDING_WINDOW;

    /**
     * Permits consumed per invocation.
     *
     * @return permit cost
     */
    int permits() default 1;

    /**
     * Optional fallback method (declared on the same bean, with the same
     * argument list) invoked when the call is rejected.
     *
     * @return fallback method name, or empty for none
     */
    String fallbackMethod() default "";
}
