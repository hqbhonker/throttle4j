# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2024-06-09

### Added
- Core rate limiting interface and configuration (`RateLimiter`, `RateLimiterConfig`)
- Fixed Window rate limiting algorithm
- Sliding Window rate limiting algorithm
- Token Bucket rate limiting algorithm
- Leaky Bucket rate limiting algorithm
- In-memory store implementation
- Redis distributed store implementation (Lettuce + Lua scripts)
- Spring Boot Starter with auto-configuration
- `@RateLimit` annotation for declarative rate limiting
- Web interceptor with `X-RateLimit-*` response headers
- Graceful fallback to local store when Redis is unavailable
- GitHub Actions CI pipeline (JDK 11 & 17 matrix)
- Comprehensive unit tests (>60% coverage)
