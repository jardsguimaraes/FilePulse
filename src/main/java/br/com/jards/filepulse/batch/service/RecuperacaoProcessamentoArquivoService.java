package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.config.RecuperacaoProcessamentoArquivoProperties;
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.exception.MovimentacaoArquivoException;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;
import br.com.jards.filepulse.watcher.enums.MotivoRejeicaoArquivo;
import br.com.jards.filepulse.watcher.service.RejeicaoArquivoService;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class RecuperacaoProcessamentoArquivoService {

    private final ProcessamentoArquivoRepository processamentoArquivoRepository;
    private final RecuperacaoProcessamentoArquivoProperties propriedadesRecuperacao;
    private final MovimentacaoArquivoService movimentacaoArquivoService;
    private final ArmazenamentoArquivoProperties propriedadesArmazenamento;
    private final HashArquivoService hashArquivoService;
    private final RejeicaoArquivoService rejeicaoArquivoService;

    public RecuperacaoProcessamentoArquivoService(ProcessamentoArquivoRepository processamentoArquivoRepository,
            RecuperacaoProcessamentoArquivoProperties propriedadesRecuperacao,
            MovimentacaoArquivoService movimentacaoArquivoService,
            ArmazenamentoArquivoProperties propriedadesArmazenamento, HashArquivoService hashArquivoService,
            RejeicaoArquivoService rejeicaoArquivoService) {
        this.processamentoArquivoRepository = processamentoArquivoRepository;
        this.propriedadesRecuperacao = propriedadesRecuperacao;
        this.movimentacaoArquivoService = movimentacaoArquivoService;
        this.propriedadesArmazenamento = propriedadesArmazenamento;
        this.hashArquivoService = hashArquivoService;
        this.rejeicaoArquivoService = rejeicaoArquivoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperar() {
        recuperarProcessamentosAbandonados();
        detectarArquivosOrfaos();
    }

    private void detectarArquivosOrfaos() {
        var diretorioProcessamento = Path.of(propriedadesArmazenamento.getDiretorioProcessamento());

        try (var arquivos = Files.list(diretorioProcessamento)) {
            arquivos.filter(Files::isRegularFile)
                    .forEach(arquivo -> {
                        processamentoArquivoRepository.buscarPorCaminhoArquivo(arquivo.toString())
                                .ifPresentOrElse(
                                        processamento -> reconciliarProcessamentoExistente(arquivo, processamento),
                                        () -> reconciliarArquivoOrfao(arquivo));
                    });
        } catch (IOException e) {
            log.error("Não foi possível verificar arquivos órfãos no diretório de processamento: {}",
                    diretorioProcessamento, e);
        }
    }

    private void reconciliarArquivoOrfao(Path arquivo) {
        var hashArquivo = hashArquivoService.calcular(arquivo);

        var existePorHash = processamentoArquivoRepository.existePorHash(hashArquivo);

        var motivo = existePorHash
                ? MotivoRejeicaoArquivo.ARQUIVO_ORFAO_DUPLICADO
                : MotivoRejeicaoArquivo.ARQUIVO_ORFAO_PROCESSAMENTO;

        var detalhes = existePorHash
                ? "Arquivo órfão com conteúdo já existente no histórico de processamento"
                : "Arquivo encontrado no diretório de processamento sem registro correspondente";

        recuperarArquivoOrfao(arquivo, hashArquivo, motivo, detalhes);
    }

    private void reconciliarProcessamentoExistente(Path arquivo, ProcessamentoArquivo processamento) {

        switch (processamento.status()) {
            case COMPLETO, COMPLETO_COM_FALHAS ->
                reconciliarProcessamentoFinalizado(
                        arquivo, processamento, true);
            case FALHOU, ABANDONADO ->
                reconciliarProcessamentoFinalizado(
                        arquivo, processamento, false);
            default ->
                log.warn(
                        "Processamento existente ainda não possui estratégia de reconciliação. id: {}, status: {}, arquivo: {}",
                        processamento.id(),
                        processamento.status(),
                        arquivo);
        }
    }

    private void reconciliarProcessamentoFinalizado(Path arquivo, ProcessamentoArquivo processamento, boolean sucesso) {

        var arquivoFinal = sucesso
                ? movimentacaoArquivoService.moverParaProcessados(arquivo)
                : movimentacaoArquivoService.moverParaErro(arquivo);

        var atualizado = processamentoArquivoRepository.atualizarCaminhoFinalArquivo(
                processamento.id(),
                arquivoFinal.toString(),
                processamento.status());

        if (atualizado) {
            log.warn(
                    "Processamento reconciliado. id: {}, status: {}, origem: {}, destino: {}",
                    processamento.id(),
                    processamento.status(),
                    arquivo,
                    arquivoFinal);
        } else {
            log.warn(
                    "Caminho final não foi atualizado durante reconciliação. id: {}, status: {}, arquivo: {}",
                    processamento.id(),
                    processamento.status(),
                    arquivo);
        }
    }

    private void recuperarArquivoOrfao(Path arquivo, String hashArquivo, MotivoRejeicaoArquivo motivo,
            String detalhes) {
        var arquivoFinal = movimentacaoArquivoService.moverParaErro(arquivo);

        rejeicaoArquivoService.auditar(arquivoFinal, hashArquivo, motivo, detalhes);

        log.warn("Arquivo órfão recuperado. origem: {}, destino: {}, motivo: {}", arquivo, arquivoFinal, motivo);
    }

    private void recuperarProcessamentosAbandonados() {

        var iniciadoAntesDe = LocalDateTime.now().minusMinutes(propriedadesRecuperacao.getAbandonedAfterMinutes());

        var candidatos = processamentoArquivoRepository.buscarCandidatosAbandonados(iniciadoAntesDe);

        if (candidatos.isEmpty()) {
            log.info("Nenhum processamento abandonado encontrado.");
            return;
        }

        log.warn(
                "{} processamento(s) candidato(s) a abandono encontrado(s).", candidatos.size());

        for (var processamento : candidatos) {

            var idProcessamentoArquivo = processamento.idProcessamentoArquivo();
            var idTentativa = processamento.idTentativa();
            var arquivo = processamento.arquivo();

            try {

                if (!Files.isRegularFile(arquivo)) {
                    log.error(
                            "Processamento candidato a abandono sem arquivo disponível. id: {}, tentativa: {}, arquivo: {}",
                            idProcessamentoArquivo,
                            idTentativa,
                            arquivo);
                    continue;
                }

                boolean atualizado = processamentoArquivoRepository.marcarComoAbandonado(
                        idProcessamentoArquivo,
                        idTentativa,
                        "Processamento interrompido antes da finalização da aplicação");

                if (!atualizado) {
                    log.warn(
                            "Processamento não foi marcado como ABANDONADO. O estado ou a tentativa ativa pode ter mudado. "
                                    + "id: {}, tentativa: {}",
                            idProcessamentoArquivo,
                            idTentativa);
                    continue;
                }

                Path arquivoFinal = movimentacaoArquivoService.moverParaErro(arquivo);

                boolean caminhoAtualizado = processamentoArquivoRepository
                        .atualizarCaminhoFinalArquivo(
                                idProcessamentoArquivo,
                                arquivoFinal.toString(),
                                StatusProcessamentoArquivo.ABANDONADO);

                if (!caminhoAtualizado) {
                    log.error(
                            "Arquivo movimentado, mas o caminho final não foi atualizado. id: {}, tentativa: {}, destino: {}",
                            idProcessamentoArquivo,
                            idTentativa,
                            arquivoFinal);
                    continue;
                }

                log.warn(
                        "Processamento recuperado como ABANDONADO. id: {}, tentativa: {}, origem: {}, destino: {}",
                        idProcessamentoArquivo,
                        idTentativa,
                        arquivo,
                        arquivoFinal);

            } catch (MovimentacaoArquivoException e) {

                log.error(
                        "Falha ao movimentar arquivo de processamento abandonado. id: {}, tentativa: {}, arquivo: {}. "
                                + "O registro poderá permanecer ABANDONADO sem caminho final.",
                        idProcessamentoArquivo,
                        idTentativa,
                        arquivo,
                        e);

            } catch (Exception e) {

                log.error(
                        "Erro inesperado durante a recuperação do processamento. id: {}, tentativa: {}, arquivo: {}",
                        idProcessamentoArquivo,
                        idTentativa,
                        arquivo,
                        e);
            }
        }
    }
}
