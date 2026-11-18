package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.config.RecuperacaoProcessamentoArquivoProperties;
import br.com.jards.filepulse.batch.enums.AcaoReconciliacaoBatch;
import br.com.jards.filepulse.batch.enums.SituacaoAbandonoExecucao;
import br.com.jards.filepulse.batch.enums.SituacaoExecucaoBatch;
import br.com.jards.filepulse.batch.enums.SituacaoInstanciaFilePulse;
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.exception.MovimentacaoArquivoException;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.InstanciaFilePulseRepository;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;
import br.com.jards.filepulse.watcher.enums.MotivoRejeicaoArquivo;
import br.com.jards.filepulse.watcher.service.RejeicaoArquivoService;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class RecuperacaoProcessamentoArquivoService {

    private final ControleExecucaoJobService controleExecucaoJobService;
    private final ProcessamentoArquivoRepository processamentoArquivoRepository;
    private final RecuperacaoProcessamentoArquivoProperties propriedadesRecuperacao;
    private final MovimentacaoArquivoService movimentacaoArquivoService;
    private final ArmazenamentoArquivoProperties propriedadesArmazenamento;
    private final HashArquivoService hashArquivoService;
    private final RejeicaoArquivoService rejeicaoArquivoService;
    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final InstanciaFilePulseRepository instanciaFilePulseRepository;
    private final IdentidadeInstanciaService identidadeInstanciaService;

    public RecuperacaoProcessamentoArquivoService(ProcessamentoArquivoRepository processamentoArquivoRepository,
            RecuperacaoProcessamentoArquivoProperties propriedadesRecuperacao,
            MovimentacaoArquivoService movimentacaoArquivoService,
            ArmazenamentoArquivoProperties propriedadesArmazenamento, HashArquivoService hashArquivoService,
            RejeicaoArquivoService rejeicaoArquivoService, JobExplorer jobExplorer, JobRepository jobRepository,
            ControleExecucaoJobService controleExecucaoJobService,
            InstanciaFilePulseRepository instanciaFilePulseRepository,
            IdentidadeInstanciaService identidadeInstanciaService) {
        this.processamentoArquivoRepository = processamentoArquivoRepository;
        this.propriedadesRecuperacao = propriedadesRecuperacao;
        this.movimentacaoArquivoService = movimentacaoArquivoService;
        this.propriedadesArmazenamento = propriedadesArmazenamento;
        this.hashArquivoService = hashArquivoService;
        this.rejeicaoArquivoService = rejeicaoArquivoService;
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.controleExecucaoJobService = controleExecucaoJobService;
        this.instanciaFilePulseRepository = instanciaFilePulseRepository;
        this.identidadeInstanciaService = identidadeInstanciaService;
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
            var arquivo = localizarArquivoTentativa(processamento.arquivo(), processamento.arquivoOrigemTentativa());
            UUID idInstancia = processamento.idInstancia();

            SituacaoInstanciaFilePulse situacaoInstancia = classificarInstancia(idInstancia);

            log.info(
                    "Verificação da instância responsável. Processamento: {}, tentativa: {}, instância: {}, situação: {}",
                    idProcessamentoArquivo,
                    idTentativa,
                    idInstancia,
                    situacaoInstancia);

            if (situacaoInstancia == SituacaoInstanciaFilePulse.HEARTBEAT_RECENTE) {

                log.info(
                        "Recuperação adiada: instância responsável apresenta heartbeat recente. Processamento: {}, instância: {}",
                        idProcessamentoArquivo,
                        idInstancia);

                continue;
            }

            try {

                if (arquivo == null) {
                    log.warn("Não foi possível localizar o arquivo da tentativa. Processamento: {}, tentativa: {},"
                            + "caminho operacional: {}, origem: {}",
                            idProcessamentoArquivo,
                            idTentativa,
                            processamento.arquivo(),
                            processamento.arquivoOrigemTentativa());

                    continue;
                }

                if (validarIntegridadeArquivo(arquivo, idProcessamentoArquivo)) {
                    log.error(
                            "Recuperação interrompida por divergência de integridade. Processamento: {}, tentativa: {}",
                            idProcessamentoArquivo,
                            idTentativa);

                    continue;
                }

                if (controleExecucaoJobService.estaEmExecucao(idTentativa)) {
                    log.debug(
                            "Recuperação ignorada: tentativa ainda controlada pela instância atual. Processamento: {}, tentativa: {}",
                            idProcessamentoArquivo,
                            idTentativa);

                    continue;
                }

                var situacaoBatch = classificarExecucaoBatch(idProcessamentoArquivo, idTentativa);

                var acaoReconciliacao = determinarAcaoReconciliacao(
                        situacaoBatch);

                log.warn(
                        "Reconciliação identificada.Processamento: {}, tentativa: {}, situação Batch: {}, ação necessária: {}",
                        idProcessamentoArquivo,
                        idTentativa,
                        situacaoBatch,
                        acaoReconciliacao);

                if (situacaoBatch != SituacaoExecucaoBatch.NAO_LOCALIZADA) {
                    log.warn(
                            "Processamento requer reconciliação com o Spring Batch. Processamento: {}, tentativa: {}, situação: {}",
                            idProcessamentoArquivo,
                            idTentativa,
                            situacaoBatch);

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

                Path arquivoFinal = garantirArquivoNoDiretorioErro(arquivo);

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

    private Path localizarArquivoTentativa(Path arquivoAtual, Path arquivoOrigemTentativa) {

        if (Files.isRegularFile(arquivoAtual)) {
            return arquivoAtual;
        }

        if (arquivoOrigemTentativa != null && Files.isRegularFile(arquivoOrigemTentativa)) {
            log.warn("Arquivo não encontrado no caminho operacional. Utilizando caminho de origem registrado: {}",
                    arquivoOrigemTentativa);

            return arquivoOrigemTentativa;
        }

        return null;
    }

    private boolean validarIntegridadeArquivo(Path arquivo, Long idProcessamentoArquivo) {
        var processamento = processamentoArquivoRepository.buscarPorId(idProcessamentoArquivo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Processamento não encontrado: %d".formatted(idProcessamentoArquivo)));

        String hashEsperado = processamento.hashArquivo();
        String hashAtual = hashArquivoService.calcular(arquivo);

        if (!hashEsperado.equals(hashAtual)) {
            log.error("Integridade inválida durante recuperação. Processamento: {}, arquivo: {}, "
                    + "hash esperado: {}, hash encontrado: {}",
                    idProcessamentoArquivo,
                    arquivo,
                    hashEsperado,
                    hashAtual);

            return false;
        }

        return true;
    }

    private Path garantirArquivoNoDiretorioErro(Path arquivo) {
        Path diretrorioErro = Path.of(
                propriedadesArmazenamento.getDiretorioErro())
                .toAbsolutePath()
                .normalize();

        Path caminhoArquivo = arquivo.toAbsolutePath().normalize();

        if (caminhoArquivo.getParent().equals(diretrorioErro)) {
            log.info("Arquivo já está no diretório de erros: {}", caminhoArquivo);
            return caminhoArquivo;
        }

        Path arquivoErro = movimentacaoArquivoService.moverParaErro(arquivo);

        log.info("Arquivo movimentado durante recuperação. Origem: {}, destino: {}",
                arquivo,
                arquivoErro);

        return arquivoErro;
    }

    private boolean existeExecucaoBatchAtiva(Long idProcessamentoArquivo) {

        var parametros = new JobParametersBuilder()
                .addLong("fileProcessingId", idProcessamentoArquivo)
                .toJobParameters();

        var instancia = jobRepository.getJobInstance(processamentoArquivoJobNome(), parametros);

        if (instancia == null) {
            log.warn("Nenhuma JobInstance encontrada para o processamento {}", idProcessamentoArquivo);
            return false;
        }

        return jobExplorer.getJobExecutions(instancia)
                .stream()
                .anyMatch(JobExecution::isRunning);
    }

    private String processamentoArquivoJobNome() {
        return "processarArquivoJob";
    }

    private JobExecution buscarExecucaoTentativa(Long idProcessamentoArquivo, UUID idTentativa) {
        var parametros = new JobParametersBuilder().addLong("fileProcessingId", idProcessamentoArquivo)
                .toJobParameters();

        var instancia = jobRepository.getJobInstance(processamentoArquivoJobNome(), parametros);

        if (instancia == null) {
            log.warn("JobInstance não encontrada. Processamento: {}, tentativa: {}",
                    idProcessamentoArquivo,
                    idTentativa);

            return null;
        }

        return jobExplorer.getJobExecutions(instancia)
                .stream()
                .filter(execucao -> {
                    String parametroTentativa = execucao.getJobParameters().getString("attemptId");

                    return idTentativa.toString().equals(parametroTentativa);
                })
                .max(Comparator.comparing(JobExecution::getId)).orElse(null);
    }

    private SituacaoExecucaoBatch classificarExecucaoBatch(Long idProcessamentoArquivo, UUID idTentativa) {
        var execucao = buscarExecucaoTentativa(idProcessamentoArquivo, idTentativa);

        if (execucao == null) {
            return SituacaoExecucaoBatch.NAO_LOCALIZADA;
        }

        return switch (execucao.getStatus()) {
            case COMPLETED -> SituacaoExecucaoBatch.CONCLUIDA;
            case FAILED -> SituacaoExecucaoBatch.FALHOU;
            case STOPPED -> SituacaoExecucaoBatch.INTERROMPIDA;
            case STARTING, STARTED, STOPPING -> SituacaoExecucaoBatch.REGISTRADA_COMO_ATIVA;
            default -> SituacaoExecucaoBatch.INDETERMINADA;
        };
    }

    private AcaoReconciliacaoBatch determinarAcaoReconciliacao(SituacaoExecucaoBatch situacao) {
        return switch (situacao) {
            case NAO_LOCALIZADA -> AcaoReconciliacaoBatch.INVESTIGAR_EXECUCAO_AUSENTE;
            case CONCLUIDA -> AcaoReconciliacaoBatch.VERIFICAR_FINALIZACAO;
            case FALHOU -> AcaoReconciliacaoBatch.VERIFICAR_FALHA;
            case INTERROMPIDA -> AcaoReconciliacaoBatch.VERIFICAR_INTERRUPCAO;
            case REGISTRADA_COMO_ATIVA -> AcaoReconciliacaoBatch.VERIFICAR_EXECUCAO_ATIVA;
            case INDETERMINADA -> AcaoReconciliacaoBatch.INVESTIGAR_ESTADO_INDETERMINADO;
        };
    }

    private SituacaoAbandonoExecucao verificarAbandonoExecucao(Long idProcessamentoArquivo,
            UUID idTentativa) {

        if (controleExecucaoJobService.estaEmExecucao(idTentativa)) {

            log.debug(
                    "Tentativa ainda controlada pela JVM atual. Processamento: {}, tentativa: {}",
                    idProcessamentoArquivo,
                    idTentativa);

            return SituacaoAbandonoExecucao.ATIVA_LOCALMENTE;
        }

        var situacaoBatch = classificarExecucaoBatch(idProcessamentoArquivo, idTentativa);

        log.info(
                "Verificação de abandono. Processamento: {}, tentativa: {}, situação Batch: {}",
                idProcessamentoArquivo,
                idTentativa,
                situacaoBatch);

        return SituacaoAbandonoExecucao.ESTADO_INDETERMINADO;
    }

    private SituacaoInstanciaFilePulse classificarInstancia(UUID idInstancia) {

        if (idInstancia == null) {
            return SituacaoInstanciaFilePulse.DESCONHECIDA;
        }

        if (idInstancia.equals(identidadeInstanciaService.obterIdInstancia())) {

            return SituacaoInstanciaFilePulse.ATIVA_LOCALMENTE;
        }

        var instancia = instanciaFilePulseRepository.buscarPorId(idInstancia);

        if (instancia.isEmpty()) {
            return SituacaoInstanciaFilePulse.DESCONHECIDA;
        }

        var estado = instancia.get();

        if (estado.encerradaEm() != null) {
            return SituacaoInstanciaFilePulse.ENCERRAMENTO_REGISTRADO;
        }

        long segundosSemHeartbeat = Duration.between(
                estado.ultimoHeartbeatEm(),
                LocalDateTime.now()).getSeconds();

        if (segundosSemHeartbeat > 60) {
            return SituacaoInstanciaFilePulse.HEARTBEAT_EXPIRADO;
        }

        return SituacaoInstanciaFilePulse.HEARTBEAT_RECENTE;
    }

}
