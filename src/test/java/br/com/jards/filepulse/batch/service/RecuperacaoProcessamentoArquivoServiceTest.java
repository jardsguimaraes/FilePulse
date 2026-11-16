package br.com.jards.filepulse.batch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.jards.filepulse.batch.config.RecuperacaoProcessamentoArquivoProperties;
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;
import br.com.jards.filepulse.watcher.enums.MotivoRejeicaoArquivo;
import br.com.jards.filepulse.watcher.service.RejeicaoArquivoService;

@ExtendWith(MockitoExtension.class)
class RecuperacaoProcessamentoArquivoServiceTest {

    @Mock
    private ProcessamentoArquivoRepository processamentoArquivoRepository;

    @Mock
    private RecuperacaoProcessamentoArquivoProperties propriedadesRecuperacao;

    @Mock
    private MovimentacaoArquivoService movimentacaoArquivoService;

    @Mock
    private ArmazenamentoArquivoProperties propriedadesArmazenamento;

    @Mock
    private HashArquivoService hashArquivoService;

    @Mock
    private RejeicaoArquivoService rejeicaoArquivoService;

    @InjectMocks
    private RecuperacaoProcessamentoArquivoService service;

    @TempDir
    Path diretorioTemporario;

    private Path diretorioProcessamento;
    private Path diretorioErro;

    @BeforeEach
    void configurarDiretorios() throws IOException {
        diretorioProcessamento = Files.createDirectories(
                diretorioTemporario.resolve("processamento"));

        diretorioErro = Files.createDirectories(
                diretorioTemporario.resolve("erro"));
    }

    @Test
    void naoDeveMarcarAbandonoQuandoNaoExistemCandidatos() {
        when(propriedadesRecuperacao.getAbandonedAfterMinutes()).thenReturn(30L);

        when(processamentoArquivoRepository.buscarCandidatosAbandonados(
                any(LocalDateTime.class)))
                .thenReturn(List.of());

        when(propriedadesArmazenamento.getDiretorioProcessamento()).thenReturn(diretorioTemporario.toString());

        assertDoesNotThrow(() -> service.recuperar());

        verify(processamentoArquivoRepository, never())
                .marcarComoAbandonado(
                        anyLong(),
                        anyString(),
                        anyString());
    }

    @Test
    void naoDeveMarcarComoAbandonadoQuandoArquivoNaoExiste() {
        var processamento = new ProcessamentoArquivo(
                1L,
                "dados.csv",
                diretorioTemporario.resolve("inexistente.csv").toString(),
                null,
                "hash-teste",
                StatusProcessamentoArquivo.PROCESSANDO,
                0L,
                0L,
                0L,
                LocalDateTime.now().minusHours(2),
                null,
                null);

        when(propriedadesRecuperacao.getAbandonedAfterMinutes()).thenReturn(30L);

        when(processamentoArquivoRepository.buscarCandidatosAbandonados(
                any(LocalDateTime.class)))
                .thenReturn(List.of(processamento));

        when(propriedadesArmazenamento.getDiretorioProcessamento()).thenReturn(diretorioTemporario.toString());

        service.recuperar();

        verify(processamentoArquivoRepository, never())
                .marcarComoAbandonado(
                        anyLong(),
                        anyString(),
                        anyString());

        verify(movimentacaoArquivoService, never()).moverParaErro(any(Path.class));
    }

    @Test
    void deveMarcarComoAbandonadoQuandoArquivoExiste() throws IOException {
        Path arquivo = Files.createFile(diretorioTemporario.resolve("dados.csv"));

        Path arquivoErro = diretorioTemporario.resolve("erro.csv");

        var processamento = criarProcessamento(1L, arquivo, StatusProcessamentoArquivo.PROCESSANDO);

        when(propriedadesRecuperacao.getAbandonedAfterMinutes()).thenReturn(30L);

        when(processamentoArquivoRepository.buscarCandidatosAbandonados(
                any(LocalDateTime.class)))
                .thenReturn(List.of(processamento));

        configurarDiretorioProcessamento();

        when(movimentacaoArquivoService.moverParaErro(arquivo)).thenReturn(arquivoErro);

        when(processamentoArquivoRepository.marcarComoAbandonado(
                eq(1L), anyString(), eq(arquivoErro.toString()))).thenReturn(true);

        service.recuperar();

        verify(movimentacaoArquivoService).moverParaErro(arquivo);

        verify(processamentoArquivoRepository).marcarComoAbandonado(
                eq(1L),
                anyString(),
                eq(arquivoErro.toString()));
    }

    @Test
    void deveIdentificarQuandoMarcacaoDeAbandonoForRecusada()
            throws IOException {

        Path arquivo = Files.createFile(diretorioTemporario.resolve("dados.csv"));

        Path arquivoErro = diretorioTemporario.resolve("erro.csv");

        var processamento = criarProcessamento(1L, arquivo, StatusProcessamentoArquivo.PROCESSANDO);

        when(propriedadesRecuperacao.getAbandonedAfterMinutes()).thenReturn(30L);

        when(processamentoArquivoRepository.buscarCandidatosAbandonados(
                any(LocalDateTime.class)))
                .thenReturn(List.of(processamento));

        configurarDiretorioProcessamento();

        when(movimentacaoArquivoService.moverParaErro(arquivo)).thenReturn(arquivoErro);

        when(processamentoArquivoRepository.marcarComoAbandonado(
                eq(1L), anyString(), anyString())).thenReturn(false);

        service.recuperar();

        verify(processamentoArquivoRepository).marcarComoAbandonado(eq(1L), anyString(), eq(arquivoErro.toString()));
    }

    @Test
    void deveAuditarArquivoOrfaoDuplicado() throws IOException {
        Path arquivo = Files.createFile(
                diretorioProcessamento.resolve("dados.csv"));

        Path arquivoErro = diretorioTemporario.resolve("erro.csv");

        configurarDiretorioProcessamento();

        when(processamentoArquivoRepository.buscarPorCaminhoArquivo(
                arquivo.toString())).thenReturn(Optional.empty());

        when(hashArquivoService.calcular(arquivo)).thenReturn("hash-duplicado");

        when(processamentoArquivoRepository.existePorHash("hash-duplicado"))
                .thenReturn(true);

        when(movimentacaoArquivoService.moverParaErro(arquivo)).thenReturn(arquivoErro);

        service.recuperar();

        verify(rejeicaoArquivoService).auditar(
                eq(arquivoErro),
                eq("hash-duplicado"),
                eq(MotivoRejeicaoArquivo.ARQUIVO_ORFAO_DUPLICADO),
                anyString());
    }

    @Test
    void deveAuditarArquivoOrfaoSemRegistro() throws IOException {
        Path arquivo = Files.createFile(
                diretorioProcessamento.resolve("dados.csv"));

        Path arquivoErro = diretorioTemporario.resolve("erro.csv");

        configurarDiretorioProcessamento();

        when(processamentoArquivoRepository.buscarPorCaminhoArquivo(
                arquivo.toString())).thenReturn(Optional.empty());

        when(hashArquivoService.calcular(arquivo)).thenReturn("hash-novo");

        when(processamentoArquivoRepository.existePorHash("hash-novo"))
                .thenReturn(false);

        when(movimentacaoArquivoService.moverParaErro(arquivo)).thenReturn(arquivoErro);

        service.recuperar();

        verify(rejeicaoArquivoService).auditar(
                eq(arquivoErro),
                eq("hash-novo"),
                eq(MotivoRejeicaoArquivo.ARQUIVO_ORFAO_PROCESSAMENTO),
                anyString());
    }

    @ParameterizedTest
    @EnumSource(value = StatusProcessamentoArquivo.class, names = { "COMPLETO", "FALHOU", "ABANDONADO" })
    void deveReconciliarProcessamentoFinalizado(
            StatusProcessamentoArquivo status) throws IOException {

        Path arquivo = Files.createFile(
                diretorioProcessamento.resolve("dados.csv"));

        Path destino = diretorioTemporario.resolve("destino.csv");

        var processamento = criarProcessamento(1L, arquivo, status);

        configurarDiretorioProcessamento();

        when(processamentoArquivoRepository.buscarPorCaminhoArquivo(
                arquivo.toString())).thenReturn(Optional.of(processamento));

        if (status == StatusProcessamentoArquivo.COMPLETO) {
            when(movimentacaoArquivoService.moverParaProcessados(arquivo)).thenReturn(destino);
        } else {
            when(movimentacaoArquivoService.moverParaErro(arquivo)).thenReturn(destino);
        }

        service.recuperar();

        verify(processamentoArquivoRepository)
                .atualizarCaminhoFinalArquivo(1L, destino.toString(), status);
    }

    @Test
    void deveReconciliarProcessamentoCompletoComFalhas()
            throws IOException {

        Path arquivo = Files.createFile(
                diretorioProcessamento.resolve("dados.csv"));

        Path destino = diretorioTemporario
                .resolve("processados")
                .resolve("dados.csv");

        var processamento = criarProcessamento(
                1L,
                arquivo,
                StatusProcessamentoArquivo.COMPLETO_COM_FALHAS);

        configurarDiretorioProcessamento();

        when(processamentoArquivoRepository.buscarPorCaminhoArquivo(
                arquivo.toString()))
                .thenReturn(Optional.of(processamento));

        when(movimentacaoArquivoService.moverParaProcessados(arquivo))
                .thenReturn(destino);

        service.recuperar();

        verify(movimentacaoArquivoService)
                .moverParaProcessados(arquivo);

        verify(processamentoArquivoRepository)
                .atualizarCaminhoFinalArquivo(
                        1L,
                        destino.toString(),
                        StatusProcessamentoArquivo.COMPLETO_COM_FALHAS);

        verify(movimentacaoArquivoService, never())
                .moverParaErro(any(Path.class));
    }

    private void configurarDiretorioProcessamento() {
        when(propriedadesArmazenamento.getDiretorioProcessamento())
                .thenReturn(diretorioProcessamento.toString());
    }

    private ProcessamentoArquivo criarProcessamento(
            Long id,
            Path arquivo,
            StatusProcessamentoArquivo status) {

        return new ProcessamentoArquivo(
                id,
                arquivo.getFileName().toString(),
                arquivo.toString(),
                null,
                "hash-teste",
                status,
                0L,
                0L,
                0L,
                LocalDateTime.now().minusHours(2),
                null,
                null);
    }

}
