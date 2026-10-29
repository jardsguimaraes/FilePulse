package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.jards.filepulse.batch.enums.FileProcessingStatus;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;

@SpringBootTest
@Testcontainers
@Transactional
class FileProcessingRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FileProcessingRepository fileProcessingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deveContarCorretamenteOsProcessamentosCadastrados() {

        long totalInicial = fileProcessingRepository.countAll();

        jdbcTemplate.update("""
                INSERT INTO file_processing
                    (file_name, file_path, file_hash, status)
                VALUES (?, ?, ?, ?)
                """,
                "clientes-1.csv",
                "/entrada/clientes-1.csv",
                "hash-teste-contagem-1",
                "COMPLETED");

        jdbcTemplate.update("""
                INSERT INTO file_processing
                    (file_name, file_path, file_hash, status)
                VALUES (?, ?, ?, ?)
                """,
                "clientes-2.csv",
                "/entrada/clientes-2.csv",
                "hash-teste-contagem-2",
                "FAILED");

        long totalFinal = fileProcessingRepository.countAll();

        assertEquals(totalInicial + 2, totalFinal);
    }

    @Test
    void deveRetornarProcessamentosOrdenadosPorIdDecrescente() {

        jdbcTemplate.update("""
                INSERT INTO file_processing
                    (file_name, file_path, file_hash, status)
                VALUES (?, ?, ?, ?)
                """,
                "clientes-ordenacao-1.csv",
                "/entrada/clientes-ordenacao-1.csv",
                "hash-teste-ordenacao-1",
                "COMPLETED");

        jdbcTemplate.update("""
                INSERT INTO file_processing
                    (file_name, file_path, file_hash, status)
                VALUES (?, ?, ?, ?)
                """,
                "clientes-ordenacao-2.csv",
                "/entrada/clientes-ordenacao-2.csv",
                "hash-teste-ordenacao-2",
                "FAILED");

        jdbcTemplate.update("""
                INSERT INTO file_processing
                    (file_name, file_path, file_hash, status)
                VALUES (?, ?, ?, ?)
                """,
                "clientes-ordenacao-3.csv",
                "/entrada/clientes-ordenacao-3.csv",
                "hash-teste-ordenacao-3",
                "COMPLETED");

        var processamentos = fileProcessingRepository.findAll(0, 10);

        assertEquals(3, processamentos.size());

        assertEquals("clientes-ordenacao-3.csv",
                processamentos.get(0).fileName());

        assertEquals("clientes-ordenacao-2.csv",
                processamentos.get(1).fileName());

        assertEquals("clientes-ordenacao-1.csv",
                processamentos.get(2).fileName());
    }

    @Test
    void deveRetornarQuantidadeLimitadaDeProcessamentos() {

        inserirProcessamentos(5);

        var processamentos = fileProcessingRepository.findAll(0, 2);

        assertEquals(2, processamentos.size());
        assertEquals("clientes-5.csv", processamentos.get(0).fileName());
        assertEquals("clientes-4.csv", processamentos.get(1).fileName());
    }

    @Test
    void deveRetornarProcessamentosDaSegundaPagina() {

        inserirProcessamentos(5);

        var processamentos = fileProcessingRepository.findAll(1, 2);

        assertEquals(2, processamentos.size());
        assertEquals("clientes-3.csv", processamentos.get(0).fileName());
        assertEquals("clientes-2.csv", processamentos.get(1).fileName());
    }

    @Test
    void deveRetornarUltimaPaginaComMenosRegistros() {

        inserirProcessamentos(5);

        var processamentos = fileProcessingRepository.findAll(2, 2);

        assertEquals(1, processamentos.size());
        assertEquals("clientes-1.csv", processamentos.get(0).fileName());
    }

    @Test
    void deveRetornarListaVaziaQuandoPaginaNaoExistir() {

        inserirProcessamentos(5);

        var processamentos = fileProcessingRepository.findAll(3, 2);

        assertTrue(processamentos.isEmpty());
    }

    @Test
    void deveRetornarListaVaziaQuandoNaoExistiremProcessamentos() {

        var processamentos = fileProcessingRepository.findAll(0, 10);

        assertTrue(processamentos.isEmpty());
    }

    @Test
    void deveMapearCorretamenteOsCamposDoProcessamento() {

        inserirProcessamentos(1);

        var processamentos = fileProcessingRepository.findAll(0, 10);

        assertEquals(1, processamentos.size());

        var processamento = processamentos.get(0);

        assertNotNull(processamento.id());
        assertEquals("clientes-1.csv", processamento.fileName());
        assertEquals("/entrada/clientes-1.csv", processamento.filePath());
        assertEquals("%064d".formatted(1), processamento.fileHash());
        assertEquals(FileProcessingStatus.COMPLETED, processamento.status());
    }

    private void inserirProcessamentos(int quantidade) {

        for (int i = 1; i <= quantidade; i++) {

            jdbcTemplate.update("""
                    INSERT INTO file_processing
                        (file_name, file_path, file_hash, status)
                    VALUES (?, ?, ?, ?)
                    """,
                    "clientes-" + i + ".csv",
                    "/entrada/clientes-" + i + ".csv",
                    "%064d".formatted(i),
                    "COMPLETED");
        }
    }

}
