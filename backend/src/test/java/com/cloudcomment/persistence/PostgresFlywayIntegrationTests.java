package com.cloudcomment.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresFlywayIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void applicationConnectsToPostgresAndFlywayCreatesSchemaHistory() {
        assertThat(dataSource).isNotNull();
        assertThat(flyway).isNotNull();

        String databaseVersion = jdbcTemplate.queryForObject("select version()", String.class);
        Integer schemaHistoryRows = jdbcTemplate.queryForObject("select count(*) from flyway_schema_history", Integer.class);
        Integer smokeTableRows = jdbcTemplate.queryForObject("select count(*) from flyway_smoke_test", Integer.class);
        Integer coreTableRows = jdbcTemplate.queryForObject("""
            select count(*)
            from information_schema.tables
            where table_schema = 'public'
              and table_name in (
                'app_users',
                'roles',
                'user_roles',
                'sites',
                'site_allowed_origins',
                'pages',
                'comments',
                'moderation_actions'
            )
            """, Integer.class);
        Integer roleRows = jdbcTemplate.queryForObject("""
            select count(*)
            from roles
            where name in ('OWNER', 'COMMENTER', 'MODERATOR')
            """, Integer.class);

        assertThat(databaseVersion).contains("PostgreSQL");
        assertThat(schemaHistoryRows).isEqualTo(3);
        assertThat(smokeTableRows).isZero();
        assertThat(coreTableRows).isEqualTo(8);
        assertThat(roleRows).isEqualTo(3);
    }
}
