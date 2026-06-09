package com.throttle4j.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Boot rate-limiting example.
 *
 * <p>Run this class to start the example HTTP server on port 8080 and then
 * try {@code GET /api/hello}, {@code GET /api/users}, {@code GET /api/unlimited}.</p>
 */
@SpringBootApplication
public class SpringBootExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootExampleApplication.class, args);
    }
}
