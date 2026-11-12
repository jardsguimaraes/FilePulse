package br.com.jards.filepulse.batch.listener;

import java.nio.file.Path;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.batch.repository.ProcessamentoErroRepository;
import br.com.jards.filepulse.batch.service.MovimentacaoArquivoService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class ProcessamentoArquivoJobListener implements JobExecutionListener {

        private final ProcessamentoArquivoRepository processamentoArquivoRepository;
        private final MovimentacaoArquivoService movimentacaoArquivoService;
        private final JobExplorer jobExplorer;
        private final ProcessamentoErroRepository processamentoErroRepository;

        public ProcessamentoArquivoJobListener(ProcessamentoArquivoRepository processamentoArquivoRepository,
                        MovimentacaoArquivoService movimentacaoArquivoService, JobExplorer jobExplorer,
                        ProcessamentoErroRepository processamentoErroRepository) {
                this.processamentoArquivoRepository = processamentoArquivoRepository;
                this.movimentacaoArquivoService = movimentacaoArquivoService;
                this.jobExplorer = jobExplorer;
                this.processamentoErroRepository = processamentoErroRepository;
        }

        @Override
        public void beforeJob(JobExecution execucaoJob) {
                var id = execucaoJob.getJobParameters()
                                .getLong("fileProcessingId");

                var caminhoArquivo = execucaoJob.getJobParameters()
                                .getString("filePath");

                var atualizado = processamentoArquivoRepository.marcarComoEmProcessamento(id,
                                caminhoArquivo);

                if (!atualizado) {
                        throw new IllegalStateException("Não foi possível iniciar o processamento %d"
                                        .formatted(id));
                }
        }

        @Override
        public void afterJob(JobExecution execucaoJob) {
                var id = execucaoJob.getJobParameters()
                                .getLong("fileProcessingId");

                var execucoes = jobExplorer.getJobExecutions(execucaoJob.getJobInstance());

                var etapas = execucoes.stream()
                                .flatMap(execucao -> execucao.getStepExecutions().stream())
                                .toList();

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

                processamentoArquivoRepository.finalizar(id, status, quantidadeLida, quantidadeGravada,
                                quantidadeIgnorada,
                                mensagemErro, arquivoFinal.toString());

                log.info("Processamento finalizado. ID: {}, Status FilePulse: {}, Lidos: {}, Gravados: {}, Ignorados: {}",
                                id,
                                status,
                                quantidadeLida,
                                quantidadeGravada,
                                quantidadeIgnorada);

        }
}
