package com.pointeight.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need a real Postgres, not a mock repository — mirrors
 * python-engine's own {@code testcontainers} setup (DEC-019 in docs/architecture.md), built for
 * the same reason: a repository adapter can be logically correct and still never actually persist
 * a change (a JPA mapping detail like {@code @Column(updatable = false)} is invisible to a
 * mocked-repository test, and cost real time to find once — see the Traps in CLAUDE.md).
 *
 * <p>The container is a {@code static} field, so JUnit starts it once and every subclass shares
 * the same running instance across the whole test run instead of paying startup cost per class.
 * {@code @ServiceConnection} wires its JDBC URL/credentials into the Spring context automatically
 * — no manual {@code @DynamicPropertySource} needed. Flyway runs the real migrations against it on
 * context startup, same as the real app, so {@code ddl-auto: validate} is actually exercised too,
 * not bypassed.
 */
@Testcontainers
public abstract class PostgresIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
}
