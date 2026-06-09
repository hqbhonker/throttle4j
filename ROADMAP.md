# throttle4j Roadmap

> 由于本地未安装 `gh` CLI，以下 Issue 需在 GitHub 仓库创建后手动添加。

---

## Issue 1: feat: Support Redis Cluster mode

**Labels**: `enhancement`, `redis`

### Description

Currently throttle4j-redis only supports standalone Redis. Add support for Redis Cluster mode to enable horizontal scaling in large-scale distributed environments.

### Tasks
- [ ] Add cluster connection configuration
- [ ] Adapt Lua scripts for cluster mode (hash tags)
- [ ] Add cluster-specific tests
- [ ] Update documentation

---

## Issue 2: feat: Metrics and monitoring integration

**Labels**: `enhancement`, `monitoring`

### Description

Integrate with popular monitoring systems to provide rate limiting metrics and observability.

### Planned Features
- [ ] Micrometer integration (Prometheus, Grafana)
- [ ] Expose metrics: total requests, rejected requests, current usage per key
- [ ] Spring Boot Actuator health indicator
- [ ] Grafana dashboard template

---

## Issue 3: feat: Spring Boot 3.x / Jakarta EE support

**Labels**: `enhancement`, `spring-boot`

### Description

Add support for Spring Boot 3.x which migrates from javax.* to jakarta.* namespace.

### Tasks
- [ ] Create separate spring-boot-starter-3x module or conditional compilation
- [ ] Replace javax.servlet with jakarta.servlet
- [ ] Update spring.factories to META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
- [ ] Test with Spring Boot 3.2.x
- [ ] Update minimum Java version to 17 for this module

---

## Issue 4: feat: Dynamic rate limit configuration

**Labels**: `enhancement`, `configuration`

### Description

Support dynamic rate limit configuration changes without application restart.

### Planned Features
- [ ] Watch configuration changes from Nacos/Apollo/Consul
- [ ] Runtime rate limit adjustment API
- [ ] Graceful configuration reload
- [ ] Admin dashboard for rate limit management

---

## Issue 5: docs: Publish to Maven Central

**Labels**: `documentation`, `release`

### Description

Set up the build pipeline to publish artifacts to Maven Central for easy consumption.

### Tasks
- [ ] Configure maven-deploy-plugin
- [ ] Set up GPG signing
- [ ] Configure Sonatype OSSRH
- [ ] Add GitHub Actions release workflow
- [ ] Publish v0.1.0
