package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import br.com.jards.filepulse.batch.repository.ProcessamentoErroRepository;

@SpringBootTest
class RegistroIgnoradoTransactionIntegrationTest {

    @Autowired
    private ProcessamentoErroRepository processamentoErroRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Test
    void deveDesfazerRegistroDeErroQuandoTransacaoFalhar() {

        Long idProcessamento = jdbcTemplate.queryForObject("""
                INSERT INTO file_processing (
                    file_name,
                    file_path,
                    file_hash,
                    status
                )
                VALUES (?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                "teste-rollback.csv",
                "/tmp/teste-rollback.csv",
                java.util.UUID.randomUUID().toString(),
                "PROCESSANDO");

        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> transacao.executeWithoutResult(status -> {

            processamentoErroRepository.registrar(
                    idProcessamento,
                    4L,
                    "PROCESSAMENTO",
                    "RegistroInvalidoException",
                    "CPF inválido",
                    null);

            throw new RuntimeException("Falha técnica simulada");
        }));

        Integer quantidadeErros = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM file_processing_error
                        WHERE file_processing_id = ?
                        """,
                Integer.class,
                idProcessamento);

        assertEquals(0, quantidadeErros);
    }

}
