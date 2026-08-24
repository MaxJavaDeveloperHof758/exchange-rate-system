package com.exchange.exchangeratesystem.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared real-PostgreSQL container for every test that needs a database
 * (Flyway now owns schema creation, and ExchangeRateRepository#upsert's
 * ON CONFLICT DO UPDATE is Postgres-specific SQL that no longer runs on
 * H2 at all). {@code @ServiceConnection} on a container bean wires its JDBC
 * URL/credentials into the DataSource/Flyway/JPA auto-configuration and
 * manages its lifecycle directly — no manual property registration, and no
 * {@code @Testcontainers}/{@code @Container}/{@code @DynamicPropertySource}
 * needed.
 *
 * postgres:16 matches the version this project documents/tests against in
 * its Flyway migrations; each importing test class gets its own container
 * instance (this is a plain, non-static bean, not a shared singleton), the
 * same isolation guarantee the old per-test unique H2 URLs provided.
 */
@TestConfiguration
public class PostgresTestContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16");
    }
}
