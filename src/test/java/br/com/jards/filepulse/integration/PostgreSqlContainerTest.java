package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PostgreSqlContainerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void shouldStartPostgresContainer() {
        assertTrue(postgres.isRunning());
    }

    @Test
    void shouldApplyDatabaseMigrations() {

        Integer columnCount = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                        AND table_name = 'customer'
                        AND column_name = 'file_processing_id'""",
                Integer.class);

        assertEquals(1, columnCount);
    }
}
