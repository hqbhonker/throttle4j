package com.throttle4j.example;

import com.throttle4j.core.Algorithm;
import com.throttle4j.spring.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample REST endpoints showcasing the {@link RateLimit} annotation.
 */
@RestController
@RequestMapping("/api")
public class ExampleController {

    /** Limited to 5 requests per 10 seconds via the sliding window algorithm. */
    @RateLimit(limit = 5, window = "10s", algorithm = Algorithm.SLIDING_WINDOW)
    @GetMapping("/hello")
    public String hello() {
        return "Hello, Throttle4j!";
    }

    /** Limited to 10 requests per minute via the token bucket algorithm. */
    @RateLimit(key = "user-api", limit = 10, window = "1m", algorithm = Algorithm.TOKEN_BUCKET)
    @GetMapping("/users")
    public String users() {
        return "User list";
    }

    /** No annotation: this endpoint is unlimited. */
    @GetMapping("/unlimited")
    public String unlimited() {
        return "No rate limit on this endpoint";
    }
}
