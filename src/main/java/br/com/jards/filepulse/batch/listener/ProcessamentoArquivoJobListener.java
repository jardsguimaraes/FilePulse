package br.com.jards.filepulse.batch.listener;

import java.nio.file.Path;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.batch.service.MovimentacaoArquivoService;

@Component
public class ProcessamentoArquivoJobListener implements JobExecutionListener {

        private final ProcessamentoArquivoRepository processamentoArquivoRepository;
        private final MovimentacaoArquivoService movimentacaoArquivoService;
        private final JobExplorer jobExplorer;

        public ProcessamentoArquivoJobListener(ProcessamentoArquivoRepository processamentoArquivoRepository,
                        MovimentacaoArquivoService movimentacaoArquivoService, JobExplorer jobExplorer) {
                this.processamentoArquivoRepository = processamentoArquivoRepository;
                this.movimentacaoArquivoService = movimentacaoArquivoService;
                this.jobExplorer = jobExplorer;
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

                var quantidadeIgnorada = etapas.stream()
                                .mapToLong(step -> step.getSkipCount()
                                                + step.getProcessSkipCount()
                                                + step.getWriteSkipCount())
                                .sum();

                var status = execucaoJob.getStatus() == BatchStatus.COMPLETED
                                ? StatusProcessamentoArquivo.COMPLETO
                                : StatusProcessamentoArquivo.FALHOU;

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

                if (status == StatusProcessamentoArquivo.COMPLETO) {
                        arquivoFinal = movimentacaoArquivoService.moverParaProcessados(Path.of(caminhoArquivo));
                } else {
                        arquivoFinal = movimentacaoArquivoService.moverParaErro(Path.of(caminhoArquivo));
                }

                processamentoArquivoRepository.finalizar(id, status, quantidadeLida, quantidadeGravada,
                                quantidadeIgnorada,
                                mensagemErro, arquivoFinal.toString());

        }
}
