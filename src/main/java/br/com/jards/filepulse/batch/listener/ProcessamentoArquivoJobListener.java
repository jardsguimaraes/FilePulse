package br.com.jards.filepulse.batch.listener;

import java.nio.file.Path;
import java.util.UUID;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.batch.repository.ProcessamentoErroRepository;
import br.com.jards.filepulse.batch.service.ControleExecucaoJobService;
import br.com.jards.filepulse.batch.service.MovimentacaoArquivoService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class ProcessamentoArquivoJobListener implements JobExecutionListener {

        private final ProcessamentoArquivoRepository processamentoArquivoRepository;
        private final MovimentacaoArquivoService movimentacaoArquivoService;
        private final ProcessamentoErroRepository processamentoErroRepository;
        private final ControleExecucaoJobService controleExecucaoJobService;

        public ProcessamentoArquivoJobListener(ProcessamentoArquivoRepository processamentoArquivoRepository,
                        MovimentacaoArquivoService movimentacaoArquivoService,
                        ProcessamentoErroRepository processamentoErroRepository,
                        ControleExecucaoJobService controleExecucaoJobService) {
                this.processamentoArquivoRepository = processamentoArquivoRepository;
                this.movimentacaoArquivoService = movimentacaoArquivoService;
                this.processamentoErroRepository = processamentoErroRepository;
                this.controleExecucaoJobService = controleExecucaoJobService;
        }

        @Override
        public void beforeJob(JobExecution execucaoJob) {

                Long idProcessamentoArquivo = execucaoJob
                                .getJobParameters()
                                .getLong("fileProcessingId");

                String parametroTentativa = execucaoJob
                                .getJobParameters()
                                .getString("attemptId");

                if (parametroTentativa == null || parametroTentativa.isBlank()) {
                        throw new IllegalStateException("O Job não possui identificador de tentativa");
                }

                UUID idTentativa = UUID.fromString(parametroTentativa);

                boolean tentativaAtiva = processamentoArquivoRepository.tentativaEstaAtiva(
                                idProcessamentoArquivo, idTentativa);

                if (!tentativaAtiva) {
                        throw new IllegalStateException("A tentativa %s não está ativa para o processamento %d"
                                        .formatted(idTentativa, idProcessamentoArquivo));
                }

                boolean registrada = controleExecucaoJobService.registrar(idTentativa);

                if (!registrada) {
                        throw new IllegalStateException("A tentativa %s já está registrada como em execução."
                                        .formatted(idTentativa));
                }

                log.info("Iniciando processamento. ID: {}, Tentativa: {}", idProcessamentoArquivo, idTentativa);
        }

        @Override
        public void afterJob(JobExecution execucaoJob) {

                String parametroTentativa = execucaoJob.getJobParameters().getString("attemptId");

                if (parametroTentativa == null || parametroTentativa.isBlank()) {
                        log.error("Não foi possível remover o controle da execução: attemptId ausente.");

                        return;
                }

                UUID idTentativa = UUID.fromString(parametroTentativa);

                try {

                        var id = execucaoJob.getJobParameters().getLong("fileProcessingId");

                        if (parametroTentativa == null || parametroTentativa.isBlank()) {
                                log.error("Finalização ignorada: Job sem identificador de tentativa. Processamento: {}",
                                                id);
                                return;
                        }

                        if (!processamentoArquivoRepository.tentativaEstaAtiva(id, idTentativa)) {
                                log.warn("Finalização ignorada. A tentativa {} não está mais ativa para o processamento {}",
                                                idTentativa,
                                                id);
                                return;
                        }

                        var etapas = execucaoJob.getStepExecutions();

                        var quantidadeLida = etapas.stream()
                                        .mapToLong(step -> step.getReadCount())
                                        .sum();

                        var quantidadeGravada = etapas.stream()
                                        .mapToLong(step -> step.getWriteCount())
                                        .sum();

                        var quantidadeIgnorada = processamentoErroRepository.contarPorProcessamento(id);

                        StatusProcessamentoArquivo status;

                        if (execucaoJob.getStatus() == BatchStatus.COMPLETED) {
                                status = quantidadeIgnorada > 0
                                                ? StatusProcessamentoArquivo.COMPLETO_COM_FALHAS
                                                : StatusProcessamentoArquivo.COMPLETO;
                        } else {
                                status = StatusProcessamentoArquivo.FALHOU;
                        }

                        String mensagemErro = null;
                        if (status == StatusProcessamentoArquivo.FALHOU) {
                                mensagemErro = execucaoJob.getAllFailureExceptions().stream()
                                                .map(Throwable::getMessage)
                                                .filter(mensagem -> mensagem != null && !mensagem.isBlank())
                                                .findFirst()
                                                .orElse("Falha durante o processamento do arquivo");
                        }

                        var caminhoArquivo = execucaoJob.getJobParameters().getString("filePath");
                        Path arquivoFinal;

                        if (status == StatusProcessamentoArquivo.COMPLETO
                                        || status == StatusProcessamentoArquivo.COMPLETO_COM_FALHAS) {
                                arquivoFinal = movimentacaoArquivoService.moverParaProcessados(Path.of(caminhoArquivo));
                        } else {
                                arquivoFinal = movimentacaoArquivoService.moverParaErro(Path.of(caminhoArquivo));
                        }

                        processamentoArquivoRepository.finalizar(
                                        id,
                                        idTentativa,
                                        status,
                                        quantidadeLida,
                                        quantidadeGravada,
                                        quantidadeIgnorada,
                                        mensagemErro,
                                        arquivoFinal.toString());

                        log.info("Processamento finalizado. ID: {}, Status FilePulse: {}, Lidos: {}, Gravados: {}, Ignorados: {}",
                                        id,
                                        status,
                                        quantidadeLida,
                                        quantidadeGravada,
                                        quantidadeIgnorada);
                } finally {
                        controleExecucaoJobService.finalizar(idTentativa);

                        log.debug("Tentativa removida do controle de execução: {}", idTentativa);
                }

        }
}
