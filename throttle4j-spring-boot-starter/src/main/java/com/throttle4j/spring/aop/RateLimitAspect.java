package com.throttle4j.spring.aop;

import com.throttle4j.core.Algorithm;
import com.throttle4j.core.RateExceededException;
import com.throttle4j.core.RateLimitResult;
import com.throttle4j.core.RateLimiter;
import com.throttle4j.core.RateLimiterConfig;
import com.throttle4j.core.RateLimiterRegistry;
import com.throttle4j.spring.annotation.RateLimit;
import com.throttle4j.spring.autoconfigure.Throttle4jProperties;
import com.throttle4j.spring.util.WindowParser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP advice that intercepts methods annotated with {@link RateLimit} and
 * delegates to a {@link RateLimiter} obtained from the shared
 * {@link RateLimiterRegistry}.
 *
 * <p>If the limiter rejects the call, this aspect either invokes a configured
 * fallback method on the same target bean, or throws a
 * {@link RateExceededException}.</p>
 */
@Aspect
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiterRegistry registry;
    private final Throttle4jProperties properties;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Construct the aspect.
     *
     * @param registry   shared limiter registry
     * @param properties auto-configuration properties
     */
    public RateLimitAspect(RateLimiterRegistry registry, Throttle4jProperties properties) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Around advice for any method annotated with {@link RateLimit}.
     *
     * @param pjp join point
     * @return original return value, fallback return value, or never returns when throwing
     * @throws Throwable propagated from the target invocation
     */
    @Around("@annotation(com.throttle4j.spring.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) {
            return pjp.proceed();
        }

        String key = resolveKey(annotation, pjp, method);
        RateLimiter limiter = obtainLimiter(key, annotation);
        int permits = Math.max(1, annotation.permits());
        RateLimitResult result = limiter.tryAcquire(key, permits);

        if (result.isAllowed()) {
            return pjp.proceed();
        }

        log.debug("Rate limit exceeded for key={}, retryAfterMillis={}", key, result.getRetryAfterMillis());
        if (!annotation.fallbackMethod().isEmpty()) {
            return invokeFallback(pjp, method, annotation.fallbackMethod(), result);
        }
        throw new RateExceededException(key, result);
    }

    private RateLimiter obtainLimiter(String key, RateLimit annotation) {
        RateLimiter existing = registry.get(key);
        if (existing != null) {
            return existing;
        }
        RateLimiterConfig config = buildConfig(annotation);
        return registry.register(key, config);
    }

    private RateLimiterConfig buildConfig(RateLimit annotation) {
        long windowMillis = WindowParser.parseToMillis(annotation.window());
        Algorithm algorithm = annotation.algorithm();
        RateLimiterConfig.Builder builder = RateLimiterConfig.builder()
                .algorithm(algorithm)
                .limit(annotation.limit())
                .windowMillis(windowMillis);
        if (algorithm == Algorithm.TOKEN_BUCKET) {
            // Derive a refill rate from limit/windowSeconds when none is configured.
            long perSecond = Math.max(1L, annotation.limit() * 1000L / Math.max(1L, windowMillis));
            builder.refillRate(perSecond);
        }
        return builder.build();
    }

    private String resolveKey(RateLimit annotation, ProceedingJoinPoint pjp, Method method) {
        String expr = annotation.key();
        if (expr == null || expr.isEmpty()) {
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }
        // Plain literal (no SpEL variables) can be used as-is.
        if (expr.indexOf('#') < 0 && expr.indexOf('\'') < 0 && expr.indexOf('+') < 0
                && expr.indexOf('(') < 0) {
            return expr;
        }
        try {
            Expression expression = expressionCache.computeIfAbsent(expr, parser::parseExpression);
            EvaluationContext ctx = buildEvaluationContext(pjp.getArgs(), method);
            Object value = expression.getValue(ctx);
            return value == null ? "null" : value.toString();
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL key '{}', falling back to method signature: {}",
                    expr, e.getMessage());
            return method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }
    }

    private EvaluationContext buildEvaluationContext(Object[] args, Method method) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        // Also expose positional variables p0, p1, ... and a0, a1, ...
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("p" + i, args[i]);
            ctx.setVariable("a" + i, args[i]);
        }
        return ctx;
    }

    private Object invokeFallback(ProceedingJoinPoint pjp, Method method, String fallbackName,
                                  RateLimitResult result) throws Throwable {
        Object target = pjp.getTarget();
        Class<?> targetClass = target.getClass();
        Method fallback = findFallback(targetClass, fallbackName, method.getParameterTypes());
        if (fallback == null) {
            log.warn("Fallback method '{}' not found on {}; throwing RateExceededException",
                    fallbackName, targetClass.getName());
            throw new RateExceededException(method.getName(), result);
        }
        try {
            fallback.setAccessible(true);
            return fallback.invoke(target, pjp.getArgs());
        } catch (java.lang.reflect.InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }

    private Method findFallback(Class<?> targetClass, String name, Class<?>[] paramTypes) {
        Class<?> c = targetClass;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {
                // continue
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
