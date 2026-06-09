package com.throttle4j.example;

import com.throttle4j.core.RateExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates {@link RateExceededException} into HTTP 429 responses with a
 * standard {@code Retry-After} header.
 */
@RestControllerAdvice
public class ExampleExceptionHandler {

    @ExceptionHandler(RateExceededException.class)
    public ResponseEntity<String> handleRateExceeded(RateExceededException e) {
        long retryAfterSeconds = e.getResult() != null
                ? Math.max(1L, e.getResult().getRetryAfterMillis() / 1000L)
                : 1L;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body("Rate limit exceeded. Please try again later.");
    }
}
