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
public class RegistroIgnoradoRestartIntegrationTest {

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
        void devePreservarErrosConfirmadosAposRollbackERestart() throws IOException {

                var arquivoCsv = createTestCsv();

                assertTrue(Files.exists(arquivoCsv));

                createFailureOnce10thCustomer();

                var processamentoIniciado = jobService.iniciar(
                                arquivoCsv,
                                "hash-registro-ignorado-restart-test");

                assertTrue(processamentoIniciado);

                var processamentoAposFalha = jdbcTemplate.queryForMap("""
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
                                "hash-registro-ignorado-restart-test");

                Long idProcessamentoArquivo = ((Number) processamentoAposFalha.get("id")).longValue();

                assertEquals("FALHOU", processamentoAposFalha.get("status"));

                Integer clientesAposFalha = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM customer
                                WHERE file_processing_id = ?
                                """,
                                Integer.class,
                                idProcessamentoArquivo);

                assertEquals(9, clientesAposFalha);

                Integer errosAposFalha = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM file_processing_error
                                WHERE file_processing_id = ?
                                """,
                                Integer.class,
                                idProcessamentoArquivo);

                assertEquals(1, errosAposFalha);

                Long linhaPrimeiroErro = jdbcTemplate.queryForObject("""
                                SELECT line_number
                                FROM file_processing_error
                                WHERE file_processing_id = ?
                                """,
                                Long.class,
                                idProcessamentoArquivo);

                assertEquals(7L, linhaPrimeiroErro);

                assertFalse(Files.exists(arquivoCsv));

                try (var arquivos = Files.list(errorDirectory)) {

                        var arquivosComErro = arquivos.toList();

                        assertEquals(1, arquivosComErro.size());

                        assertEquals(
                                        "clientes-restart.csv",
                                        arquivosComErro.getFirst().getFileName().toString());
                }

                jobService.reiniciar(idProcessamentoArquivo);

                Integer clientesAposRestart = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM customer
                                WHERE file_processing_id = ?
                                """,
                                Integer.class,
                                idProcessamentoArquivo);

                assertEquals(18, clientesAposRestart);

                Integer errosAposRestart = jdbcTemplate.queryForObject("""
                                SELECT COUNT(*)
                                FROM file_processing_error
                                WHERE file_processing_id = ?
                                """,
                                Integer.class,
                                idProcessamentoArquivo);

                assertEquals(2, errosAposRestart);

                var linhasComErro = jdbcTemplate.queryForList("""
                                SELECT line_number
                                FROM file_processing_error
                                WHERE file_processing_id = ?
                                ORDER BY line_number
                                """,
                                Long.class,
                                idProcessamentoArquivo);

                assertEquals(
                                java.util.List.of(7L, 17L),
                                linhasComErro);

                var processamentoAposRestart = jdbcTemplate.queryForMap("""
                                SELECT
                                        id,
                                        status,
                                        read_count,
                                        write_count,
                                        skip_count,
                                        error_message,
                                        final_file_path
                                FROM file_processing
                                WHERE id = ?
                                """,
                                idProcessamentoArquivo);

                assertEquals(
                                "COMPLETO_COM_FALHAS",
                                processamentoAposRestart.get("status"));

                assertEquals(
                                18L,
                                ((Number) processamentoAposRestart.get("write_count"))
                                                .longValue());

                assertEquals(
                                2L,
                                ((Number) processamentoAposRestart.get("skip_count"))
                                                .longValue());

                assertNull(processamentoAposRestart.get("error_message"));

                assertFalse(Files.exists(
                                errorDirectory.resolve("clientes-restart.csv")));

                assertTrue(Files.exists(
                                processedDirectory.resolve("clientes-restart.csv")));

                assertFalse(Files.exists(
                                processingDirectory.resolve("clientes-restart.csv")));
        }

        private void createFailureOnce10thCustomer() {
                jdbcTemplate.execute("""
                                        CREATE SEQUENCE restart_test_insert_sequence
                                        START WITH 1
                                """);

                jdbcTemplate.execute("""
                                        CREATE OR REPLACE FUNCTION fail_customer_insert_once()
                                        RETURNS TRIGGER AS $$
                                        BEGIN
                                                IF nextval('restart_test_insert_sequence') = 10 THEN
                                                RAISE EXCEPTION 'Falha técnica controlada';
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

                content.append("idCarga|CAIXA-20261112-000001\n");
                content.append("tipoArquivo|ORIGINAL\n");
                content.append("totalRegistros|20\n");
                content.append("id_registro|nome|cpf|agencia|conta|valor|mes_referencia\n");

                for (int i = 1; i <= 20; i++) {

                        String cpf = (i == 3 || i == 13)
                                        ? "123"
                                        : "52998224725";

                        content.append(
                                        "REG-%06d|Cliente %d|%s|0001|%06d|100.00|10/26%n"
                                                        .formatted(i, i, cpf, i));
                }

                return Files.writeString(file, content.toString());
        }
}
