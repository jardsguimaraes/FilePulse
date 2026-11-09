package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.batch.service.RecuperacaoProcessamentoArquivoService;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;

@SpringBootTest
@Testcontainers
class FileProcessingRecoveryIntegrationTest {

        @Container
        @ServiceConnection
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

        @Autowired
        private ProcessamentoArquivoRepository repository;

        @Autowired
        private RecuperacaoProcessamentoArquivoService recoveryService;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private ArmazenamentoArquivoProperties properties;

        @Test
        void deveReconciliarProcessamentoCompleto() throws Exception {
                testarReconciliacao(StatusProcessamentoArquivo.COMPLETO);
        }

        @Test
        void deveReconciliarProcessamentoComFalha() throws Exception {
                testarReconciliacao(StatusProcessamentoArquivo.FALHOU);
        }

        @Test
        void deveReconciliarProcessamentoAbandonado() throws Exception {
                testarReconciliacao(StatusProcessamentoArquivo.ABANDONADO);
        }

        private void testarReconciliacao(
                        StatusProcessamentoArquivo status) throws Exception {

                Path diretorioProcessamento = Path.of(properties.getDiretorioProcessamento());

                Files.createDirectories(diretorioProcessamento);

                Path arquivo = diretorioProcessamento.resolve(
                                "teste-reconciliacao-" + UUID.randomUUID() + ".csv");

                Files.writeString(arquivo, "conteudo de teste");

                Long id = repository.criar(
                                arquivo.getFileName().toString(),
                                arquivo.toString(),
                                UUID.randomUUID().toString());

                jdbcTemplate.update("""
                                UPDATE file_processing
                                SET status = ?,
                                        finished_at = CURRENT_TIMESTAMP
                                WHERE id = ?
                                """, status.name(), id);

                Path arquivoFinal = null;

                try {
                        recoveryService.recuperar();

                        var processamento = repository.buscarPorId(id)
                                        .orElseThrow();

                        assertEquals(status, processamento.status());
                        assertNotNull(processamento.caminhoFinalArquivo());

                        arquivoFinal = Path.of(
                                        processamento.caminhoFinalArquivo());

                        assertFalse(Files.exists(arquivo));
                        assertTrue(Files.exists(arquivoFinal));

                        Path diretorioEsperado = status == StatusProcessamentoArquivo.COMPLETO
                                        ? Path.of(properties.getDiretorioProcessados())
                                        : Path.of(properties.getDiretorioErro());

                        assertEquals(
                                        diretorioEsperado.toAbsolutePath().normalize(),
                                        arquivoFinal.getParent().toAbsolutePath().normalize());

                } finally {
                        Files.deleteIfExists(arquivo);

                        if (arquivoFinal != null) {
                                Files.deleteIfExists(arquivoFinal);
                        }

                        jdbcTemplate.update(
                                        "DELETE FROM file_processing WHERE id = ?", id);
                }
        }
}
