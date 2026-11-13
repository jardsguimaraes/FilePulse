package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

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
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.repository.CargaLogicaRepository;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;

@SpringBootTest(classes = FilepulseApplication.class)
@Testcontainers
class ProcessamentoArquivoRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ProcessamentoArquivoRepository repository;

    @Autowired
    private CargaLogicaRepository cargaLogicaRepository;

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

    private Long criarCarga() {
        return cargaLogicaRepository.criar(
                "TEST-" + UUID.randomUUID(), 10).id();
    }

    private String novoHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private Long criarProcessamento() {
        return repository.criar(
                "dados.csv",
                "/files/entrada/dados.csv",
                novoHash(),
                criarCarga(),
                TipoArquivoCsv.ORIGINAL);
    }

    @Test
    void deveCriarProcessamentoComStatusInicial() {
        Long id = criarProcessamento();

        var processamento = repository.buscarPorId(id).orElseThrow();

        assertNotNull(id);
        assertEquals(StatusProcessamentoArquivo.PROCESSANDO,
                processamento.status());
        assertEquals("dados.csv", processamento.nomeArquivo());
        assertEquals("/files/entrada/dados.csv",
                processamento.caminhoArquivo());
        assertEquals(0L, processamento.quantidadeLida());
        assertEquals(0L, processamento.quantidadeGravada());
        assertEquals(0L, processamento.quantidadeIgnorada());
        assertNotNull(processamento.iniciadoEm());
        assertNull(processamento.finalizadoEm());
    }

    @Test
    void deveAssociarProcessamentoACargaLogica() {
        Long idCarga = criarCarga();

        Long idProcessamento = repository.criar(
                "original.csv",
                "/files/entrada/original.csv",
                novoHash(),
                idCarga,
                TipoArquivoCsv.ORIGINAL);

        assertEquals(idCarga,
                repository.buscarIdCargaLogica(idProcessamento));

        String tipo = jdbcTemplate.queryForObject(
                "SELECT file_type FROM file_processing WHERE id = ?",
                String.class, idProcessamento);

        assertEquals("ORIGINAL", tipo);
    }

    @Test
    void deveCriarArquivoComplementar() {
        Long id = repository.criar(
                "complementar.csv",
                "/files/entrada/complementar.csv",
                novoHash(),
                criarCarga(),
                TipoArquivoCsv.COMPLEMENTAR);

        String tipo = jdbcTemplate.queryForObject(
                "SELECT file_type FROM file_processing WHERE id = ?",
                String.class, id);

        assertEquals("COMPLEMENTAR", tipo);
    }

    @Test
    void deveImpedirHashDuplicado() {
        Long idCarga = criarCarga();
        String hash = novoHash();

        repository.criar("a.csv", "/a.csv", hash,
                idCarga, TipoArquivoCsv.ORIGINAL);

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.criar(
                        "b.csv", "/b.csv", hash,
                        idCarga, TipoArquivoCsv.COMPLEMENTAR));
    }

    @Test
    void deveRejeitarCargaLogicaInexistente() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.criar(
                        "dados.csv", "/dados.csv", novoHash(),
                        -999L, TipoArquivoCsv.ORIGINAL));
    }

    @Test
    void deveRejeitarCargaLogicaNula() {
        assertThrows(NullPointerException.class,
                () -> repository.criar(
                        "dados.csv", "/dados.csv", novoHash(),
                        null, TipoArquivoCsv.ORIGINAL));
    }

    @Test
    void deveRejeitarTipoArquivoNulo() {
        assertThrows(NullPointerException.class,
                () -> repository.criar(
                        "dados.csv", "/dados.csv", novoHash(),
                        criarCarga(), null));
    }

    @Test
    void deveConsultarExistenciaPorHash() {
        String hash = novoHash();

        assertFalse(repository.existePorHash(hash));

        repository.criar("dados.csv", "/dados.csv", hash,
                criarCarga(), TipoArquivoCsv.ORIGINAL);

        assertTrue(repository.existePorHash(hash));
    }

    @Test
    void deveBuscarProcessamentoPorCaminho() {
        Long id = criarProcessamento();

        var encontrado = repository.buscarPorCaminhoArquivo(
                "/files/entrada/dados.csv");

        assertTrue(encontrado.isPresent());
        assertEquals(id, encontrado.orElseThrow().id());
    }

    @Test
    void deveRetornarVazioParaConsultasInexistentes() {
        assertTrue(repository.buscarPorId(-999L).isEmpty());
        assertTrue(repository.buscarPorCaminhoArquivo(
                "/inexistente.csv").isEmpty());
    }

    @Test
    void deveFinalizarProcessamentoComSucesso() {
        Long id = criarProcessamento();

        repository.finalizar(
                id,
                StatusProcessamentoArquivo.COMPLETO,
                10L, 10L, 0L,
                null,
                "/files/processados/dados.csv");

        var resultado = repository.buscarPorId(id).orElseThrow();

        assertEquals(StatusProcessamentoArquivo.COMPLETO,
                resultado.status());
        assertEquals(10L, resultado.quantidadeLida());
        assertEquals(10L, resultado.quantidadeGravada());
        assertEquals(0L, resultado.quantidadeIgnorada());
        assertEquals("/files/processados/dados.csv",
                resultado.caminhoFinalArquivo());
        assertNotNull(resultado.finalizadoEm());
        assertNull(resultado.mensagemErro());
    }

    @Test
    void deveRegistrarFinalizacaoComFalhas() {
        Long id = criarProcessamento();

        repository.finalizar(
                id,
                StatusProcessamentoArquivo.COMPLETO_COM_FALHAS,
                10L, 8L, 2L,
                "Dois registros ignorados",
                "/files/processados/dados.csv");

        var resultado = repository.buscarPorId(id).orElseThrow();

        assertEquals(StatusProcessamentoArquivo.COMPLETO_COM_FALHAS,
                resultado.status());
        assertEquals(10L, resultado.quantidadeLida());
        assertEquals(8L, resultado.quantidadeGravada());
        assertEquals(2L, resultado.quantidadeIgnorada());
        assertEquals("Dois registros ignorados",
                resultado.mensagemErro());
    }

    @Test
    void deveBuscarSomenteCandidatosAbandonados() {
        Long antigo = criarProcessamento();
        Long recente = criarProcessamento();
        Long finalizado = criarProcessamento();

        jdbcTemplate.update("""
                UPDATE file_processing
                SET started_at = CURRENT_TIMESTAMP - INTERVAL '2 hours'
                WHERE id = ?
                """, antigo);

        jdbcTemplate.update("""
                UPDATE file_processing
                SET started_at = CURRENT_TIMESTAMP - INTERVAL '2 hours'
                WHERE id = ?
                """, finalizado);

        repository.finalizar(
                finalizado, StatusProcessamentoArquivo.COMPLETO,
                1L, 1L, 0L, null, "/processados/finalizado.csv");

        var candidatos = repository.buscarCandidatosAbandonados(
                LocalDateTime.now().minusHours(1));

        assertTrue(candidatos.stream().anyMatch(p -> p.id().equals(antigo)));
        assertFalse(candidatos.stream().anyMatch(p -> p.id().equals(recente)));
        assertFalse(candidatos.stream().anyMatch(p -> p.id().equals(finalizado)));
    }

    @Test
    void deveMarcarProcessamentoComoAbandonado() {
        Long id = criarProcessamento();

        assertTrue(repository.marcarComoAbandonado(
                id, "Processamento interrompido",
                "/files/erro/dados.csv"));

        var resultado = repository.buscarPorId(id).orElseThrow();

        assertEquals(StatusProcessamentoArquivo.ABANDONADO,
                resultado.status());
        assertEquals("Processamento interrompido",
                resultado.mensagemErro());
        assertEquals("/files/erro/dados.csv",
                resultado.caminhoFinalArquivo());
        assertNotNull(resultado.finalizadoEm());

        assertFalse(repository.marcarComoAbandonado(
                id, "Nova tentativa", "/outro.csv"));
    }

    @Test
    void naoDeveAbandonarProcessamentoJaFinalizado() {
        Long id = criarProcessamento();

        repository.finalizar(id, StatusProcessamentoArquivo.COMPLETO,
                1L, 1L, 0L, null, "/processados/dados.csv");

        assertFalse(repository.marcarComoAbandonado(
                id, "Erro", "/erro/dados.csv"));
    }

    @Test
    void deveAtualizarCaminhoFinalUmaUnicaVez() {
        Long id = criarProcessamento();

        repository.finalizar(id, StatusProcessamentoArquivo.FALHOU,
                0L, 0L, 0L, "Falha", null);

        assertTrue(repository.atualizarCaminhoFinalArquivo(
                id, "/files/erro/dados.csv",
                StatusProcessamentoArquivo.FALHOU));

        assertFalse(repository.atualizarCaminhoFinalArquivo(
                id, "/files/erro/outro.csv",
                StatusProcessamentoArquivo.FALHOU));

        assertEquals("/files/erro/dados.csv",
                repository.buscarPorId(id).orElseThrow()
                        .caminhoFinalArquivo());
    }

    @Test
    void naoDeveAtualizarCaminhoComStatusDiferente() {
        Long id = criarProcessamento();

        assertFalse(repository.atualizarCaminhoFinalArquivo(
                id, "/files/processados/dados.csv",
                StatusProcessamentoArquivo.COMPLETO));
    }

    @Test
    void deveRetomarProcessamentoQueFalhou() {
        Long id = criarProcessamento();

        repository.finalizar(id, StatusProcessamentoArquivo.FALHOU,
                5L, 2L, 3L, "Erro anterior",
                "/files/erro/dados.csv");

        assertTrue(repository.marcarComoEmProcessamento(
                id, "/files/processamento/dados.csv"));

        var resultado = repository.buscarPorId(id).orElseThrow();

        assertEquals(StatusProcessamentoArquivo.PROCESSANDO,
                resultado.status());
        assertEquals("/files/processamento/dados.csv",
                resultado.caminhoArquivo());
        assertNull(resultado.finalizadoEm());
        assertNull(resultado.mensagemErro());
        assertNull(resultado.caminhoFinalArquivo());
    }

    @Test
    void devePermitirAtualizarProcessamentoAindaAtivo() {
        Long id = criarProcessamento();

        assertTrue(repository.marcarComoEmProcessamento(
                id, "/files/processamento/dados.csv"));

        assertEquals("/files/processamento/dados.csv",
                repository.buscarPorId(id).orElseThrow()
                        .caminhoArquivo());
    }

    @Test
    void naoDeveRetomarProcessamentoConcluidoOuAbandonado() {
        Long concluido = criarProcessamento();
        Long abandonado = criarProcessamento();

        repository.finalizar(concluido,
                StatusProcessamentoArquivo.COMPLETO,
                1L, 1L, 0L, null, "/processados/dados.csv");

        repository.marcarComoAbandonado(
                abandonado, "Interrompido", "/erro/dados.csv");

        assertFalse(repository.marcarComoEmProcessamento(
                concluido, "/processamento/dados.csv"));

        assertFalse(repository.marcarComoEmProcessamento(
                abandonado, "/processamento/dados.csv"));
    }

    @Test
    void deveListarComPaginacaoEOrdenacaoDecrescente() {
        Long primeiro = criarProcessamento();
        Long segundo = criarProcessamento();
        Long terceiro = criarProcessamento();

        var paginaZero = repository.listarTodos(0, 2);
        var paginaUm = repository.listarTodos(1, 2);

        assertEquals(2, paginaZero.size());
        assertEquals(1, paginaUm.size());
        assertEquals(terceiro, paginaZero.get(0).id());
        assertEquals(segundo, paginaZero.get(1).id());
        assertEquals(primeiro, paginaUm.get(0).id());
        assertEquals(3L, repository.contarTodos());
    }

    @Test
    void deveDesfazerCriacaoQuandoTransacaoFalhar() {
        String hash = novoHash();
        TransactionTemplate transacao = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class,
                () -> transacao.execute(status -> {
                    repository.criar(
                            "rollback.csv", "/rollback.csv",
                            hash, criarCarga(),
                            TipoArquivoCsv.ORIGINAL);

                    throw new IllegalStateException("Forçar rollback");
                }));

        assertFalse(repository.existePorHash(hash));
    }
}
