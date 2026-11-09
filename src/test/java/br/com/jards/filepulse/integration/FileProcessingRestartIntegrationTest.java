package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.jards.filepulse.batch.service.JobService;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;

@SpringBootTest
@Testcontainers
public class FileProcessingRestartIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @TempDir
    Path tempDir;

    @Autowired
    private ArmazenamentoArquivoProperties fileStorageProperties;

    @Autowired
    private JobService jobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path inputDirectory;
    private Path processingDirectory;
    private Path processedDirectory;
    private Path errorDirectory;
    private Path rejectedDirectory;

    @BeforeEach
    void setUpDirectories() throws IOException {
        inputDirectory = createDirectory("entrada");
        processingDirectory = createDirectory("processamento");
        processedDirectory = createDirectory("processados");
        errorDirectory = createDirectory("erro");
        rejectedDirectory = createDirectory("rejeitados");

        fileStorageProperties.setDiretorioEntrada(inputDirectory.toString());
        fileStorageProperties.setDiretorioProcessamento(processingDirectory.toString());
        fileStorageProperties.setDiretorioProcessados(processedDirectory.toString());
        fileStorageProperties.setDiretorioErro(errorDirectory.toString());
        fileStorageProperties.setDiretorioRejeitados(rejectedDirectory.toString());
    }

    @Test
    void shouldFailAfterThreeCommittedChunks() throws IOException {
        var csv = createTestCsv();

        assertTrue(Files.exists(csv));

        createFailureOnce31stCustomer();
        var started = jobService.iniciar(csv, "hash-restart-integration-test");

        assertTrue(started);

        // Temporaio
        var processing = jdbcTemplate.queryForMap(
                """
                        SELECT
                            id,
                            status,
                            read_count,
                            write_count,
                            skip_count,
                            error_message,
                            final_file_path
                        FROM file_processing
                        WHERE file_hash = ?
                        """,
                "hash-restart-integration-test");

        System.out.println("FILE_PROCESSING = " + processing);

        // Fim do temporario

        Long fileProcessigId = ((Number) processing.get("id")).longValue();

        assertEquals("FALHOU", processing.get("status"));

        var customers = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM customer""",
                Integer.class);

        assertEquals(30, customers);

        assertFalse(Files.exists(csv));

        try (var files = Files.list(errorDirectory)) {
            var filesInError = files.toList();
            assertEquals(1, filesInError.size());
            assertEquals("clientes-restart.csv", filesInError.getFirst().getFileName().toString());

        }

        jobService.reiniciar(fileProcessigId);

        var customersAfterRestart = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM customer
                WHERE file_processing_id = ?""",
                Integer.class,
                fileProcessigId);

        assertEquals(40, customersAfterRestart);

        var pricessingAfterRestart = jdbcTemplate.queryForMap("""
                SELECT
                    id,
                    status,
                    read_count,
                    write_count,
                    skip_count,
                    error_message,
                    final_file_path
                FROM file_processing
                WHERE id = ?""",
                fileProcessigId);

        assertEquals(40, ((Number) pricessingAfterRestart.get("write_count")).longValue());

        System.out.println("FILE_PROCESSING AFTER RESTART = %s".formatted(pricessingAfterRestart));

        assertEquals("COMPLETO", pricessingAfterRestart.get("status"));
        assertNull(pricessingAfterRestart.get("error_message"));
        assertFalse(Files.exists(errorDirectory.resolve("clientes-restart.csv")));
        assertTrue(Files.exists(processedDirectory.resolve("clientes-restart.csv")));
        assertFalse(Files.exists(processingDirectory.resolve("clientes-restart.csv")));
    }

    private void createFailureOnce31stCustomer() {
        jdbcTemplate.execute("""
                    CREATE SEQUENCE restart_test_insert_sequence
                    START WITH 1
                """);

        jdbcTemplate.execute("""
                    CREATE OR REPLACE FUNCTION fail_customer_insert_once()
                    RETURNS TRIGGER AS $$
                        BEGIN
                            IF nextval('restart_test_insert_sequence') = 31 THEN
                                RAISE EXCEPTION 'Falha controlada no registro 31';
                            END IF;

                            RETURN NEW;
                        END;
                    $$ LANGUAGE plpgsql
                """);

        jdbcTemplate.execute("""
                    CREATE TRIGGER restart_test_fail_once
                    BEFORE INSERT ON customer
                    FOR EACH ROW
                    EXECUTE FUNCTION fail_customer_insert_once()
                """);
    }

    private Path createDirectory(String name) throws IOException {
        return Files.createDirectories(tempDir.resolve(name));
    }

    private Path createTestCsv() throws IOException {
        var file = processingDirectory.resolve("clientes-restart.csv");

        var content = new StringBuilder();
        content.append("Nome|CPF|Agência|Conta|Valor|Mês de Referência\n");

        for (int i = 1; i <= 40; i++) {
            content.append("Cliente %d|52998224725|0001|%06d|100.00|10/26%n".formatted(i, i));
        }

        return Files.writeString(file, content.toString());
    }
}