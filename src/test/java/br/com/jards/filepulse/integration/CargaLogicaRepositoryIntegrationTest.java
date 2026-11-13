package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.jards.filepulse.FilepulseApplication;
import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.repository.CargaLogicaRepository;

@SpringBootTest(classes = FilepulseApplication.class)
@Testcontainers
class CargaLogicaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CargaLogicaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void limparDados() {
        jdbcTemplate.update("DELETE FROM customer");
        jdbcTemplate.update("DELETE FROM file_processing");
        jdbcTemplate.update("DELETE FROM logical_load");
    }

    @Test
    void deveCriarCargaIncompleta() {
        var carga = repository.criar(novoIdentificador(), 3);

        assertNotNull(carga.id());
        assertEquals(3L, carga.totalRegistros());
        assertEquals(StatusCargaLogica.INCOMPLETA, carga.status());
    }

    @Test
    void deveBuscarCargaPeloIdentificador() {
        var criada = repository.criar(novoIdentificador(), 5);

        var encontrada = repository.buscarPorIdCarga(criada.idCarga());

        assertTrue(encontrada.isPresent());
        assertEquals(criada, encontrada.orElseThrow());
    }

    @Test
    void deveRetornarVazioQuandoCargaNaoExiste() {
        var resultado = repository.buscarPorIdCarga("INEXISTENTE");

        assertTrue(resultado.isEmpty());
    }

    @Test
    void deveImpedirIdentificadorDuplicado() {
        String identificador = novoIdentificador();

        repository.criar(identificador, 3);

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.criar(identificador, 3));
    }

    @Test
    void deveRejeitarTotalZero() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.criar(novoIdentificador(), 0));
    }

    @Test
    void deveRejeitarTotalNegativo() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.criar(novoIdentificador(), -1));
    }

    @Test
    void deveManterIncompletaSemRegistros() {
        var carga = repository.criar(novoIdentificador(), 2);

        assertFalse(repository.concluirSeCompleta(carga.id()));
        assertEquals(StatusCargaLogica.INCOMPLETA,
                buscarStatus(carga.id()));
    }

    @Test
    void deveManterIncompletaComRegistrosInsuficientes() {
        var carga = repository.criar(novoIdentificador(), 2);

        inserirCliente(carga.id(), "REG-000001");

        assertFalse(repository.concluirSeCompleta(carga.id()));
        assertEquals(StatusCargaLogica.INCOMPLETA,
                buscarStatus(carga.id()));
    }

    @Test
    void deveConcluirQuandoQuantidadeForExata() {
        var carga = repository.criar(novoIdentificador(), 2);

        inserirCliente(carga.id(), "REG-000001");
        inserirCliente(carga.id(), "REG-000002");

        assertTrue(repository.concluirSeCompleta(carga.id()));
        assertEquals(StatusCargaLogica.CONCLUIDA,
                buscarStatus(carga.id()));

        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT completed_at FROM logical_load WHERE id = ?",
                java.sql.Timestamp.class,
                carga.id()));
    }

    @Test
    void deveImpedirConclusaoComProcessamentoAtivo() {
        var carga = repository.criar(novoIdentificador(), 1);

        inserirCliente(carga.id(), "REG-000001");
        inserirProcessamento(carga.id(), "PROCESSANDO");

        assertFalse(repository.concluirSeCompleta(carga.id()));
        assertEquals(StatusCargaLogica.INCOMPLETA,
                buscarStatus(carga.id()));
    }

    @Test
    void deveImpedirNovaConclusaoDeCargaConcluida() {
        var carga = repository.criar(novoIdentificador(), 1);

        inserirCliente(carga.id(), "REG-000001");

        assertTrue(repository.concluirSeCompleta(carga.id()));
        assertFalse(repository.concluirSeCompleta(carga.id()));
    }

    @Test
    void deveDesfazerCriacaoQuandoTransacaoFalhar() {
        String identificador = novoIdentificador();

        var transacao = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transacao.execute(status -> {
            repository.criar(identificador, 2);
            throw new IllegalStateException("Falha simulada");
        }));

        assertTrue(repository.buscarPorIdCarga(identificador).isEmpty());
    }

    @Test
    void deveBloquearSegundaTransacaoAtePrimeiraFinalizar()
            throws Exception {

        var carga = repository.criar(novoIdentificador(), 2);

        var bloqueioAdquirido = new CountDownLatch(1);
        var liberarPrimeira = new CountDownLatch(1);
        var segundaIniciada = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);

        try {
            var primeira = executor.submit(() -> {
                var transacao = new TransactionTemplate(transactionManager);

                transacao.executeWithoutResult(status -> {
                    repository.bloquearCarga(carga.id());
                    bloqueioAdquirido.countDown();

                    try {
                        if (!liberarPrimeira.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Tempo esgotado aguardando liberação");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
            });

            assertTrue(bloqueioAdquirido.await(10, TimeUnit.SECONDS));

            var segunda = executor.submit(() -> {
                var transacao = new TransactionTemplate(transactionManager);

                transacao.executeWithoutResult(status -> {
                    segundaIniciada.countDown();
                    repository.bloquearCarga(carga.id());
                });
            });

            assertTrue(segundaIniciada.await(10, TimeUnit.SECONDS));

            assertThrows(
                    java.util.concurrent.TimeoutException.class,
                    () -> segunda.get(300, TimeUnit.MILLISECONDS));

            liberarPrimeira.countDown();

            primeira.get(10, TimeUnit.SECONDS);
            segunda.get(10, TimeUnit.SECONDS);

        } finally {
            liberarPrimeira.countDown();
            executor.shutdownNow();
        }
    }

    // =====================================================
    // MÉTODOS AUXILIARES
    // =====================================================

    private String novoIdentificador() {
        return "TEST-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private StatusCargaLogica buscarStatus(Long idCarga) {
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM logical_load WHERE id = ?",
                String.class,
                idCarga);

        return StatusCargaLogica.valueOf(status);
    }

    private void inserirCliente(Long idCarga, String identificador) {
        jdbcTemplate.update("""
                INSERT INTO customer (
                    name, cpf, agency, account, value,
                    reference_month, logical_load_id, record_identifier
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "Cliente Teste",
                "12345678901",
                "0001",
                "12345",
                100.00,
                "11/26",
                idCarga,
                identificador);
    }

    private void inserirProcessamento(Long idCarga, String status) {
        String identificador = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO file_processing (
                    file_name, file_path, file_hash,
                    status, logical_load_id, file_type
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "teste.csv",
                "/tmp/teste.csv",
                identificador,
                status,
                idCarga,
                "ORIGINAL");
    }
}
