package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.exception.JobLaunchException;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class JobService {

    private final JobLauncher jobLauncher;
    private final Job fileProcessJob;
    private final ProcessamentoArquivoRepository fileProcessingRepository;
    private final MovimentacaoArquivoService fileMovementService;

    public JobService(JobLauncher jobLauncher, @Qualifier("processarArquivoJob") Job fileProcessJob,
            ProcessamentoArquivoRepository fileProcessingRepository, MovimentacaoArquivoService fileMovementService) {
        this.jobLauncher = jobLauncher;
        this.fileProcessJob = fileProcessJob;
        this.fileProcessingRepository = fileProcessingRepository;
        this.fileMovementService = fileMovementService;
    }

    public boolean start(Path file, String fileHash) {

        Long fileProcessingId;

        try {
            fileProcessingId = fileProcessingRepository.criar(
                    file.getFileName().toString(),
                    file.toString(),
                    fileHash);

        } catch (DuplicateKeyException e) {
            log.warn("Processamento não iniciado. Já existe um arquivo registrado com o hash: {}", fileHash);
            return false;
        }

        var parametros = buildJobParameters(file, fileProcessingId);

        try {
            jobLauncher.run(fileProcessJob, parametros);
            return true;
        } catch (JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException e) {
            log.error("Erro ao iniciar o Job para o arquivo {}", file, e);
            throw new JobLaunchException("Não foi possível iniciar o Job para o arquivo %s".formatted(file), e);
        }
    }

    public void restart(Long fileProcessingId) {
        var processing = fileProcessingRepository.buscarPorId(fileProcessingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Processamento não encontrado: %s".formatted(fileProcessingId)));

        if (processing.status() != StatusProcessamentoArquivo.FALHOU) {
            throw new IllegalStateException(
                    "Apenas processamentos com status FALHOU podem ser reiniciados. Status atual: %s"
                            .formatted(processing.status()));
        }

        var finalFilePath = processing.caminhoFinalArquivo();

        if (finalFilePath == null || finalFilePath.isBlank()) {
            throw new IllegalStateException(
                    "Processamento não possui arquivo disponível para restart: %s".formatted(fileProcessingId));
        }

        Path processingFile = null;

        try {
            processingFile = fileMovementService.moveToProcessing(Path.of(finalFilePath));
            var parametros = buildJobParameters(processingFile, fileProcessingId);
            jobLauncher.run(fileProcessJob, parametros);
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                | JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {

            rollbackFileToError(processingFile, fileProcessingId);

            log.error("Erro ao reiniciar o processamento {}", fileProcessingId, e);
            throw new JobLaunchException("Não foi possível reiniciar o processamento %d".formatted(fileProcessingId),
                    e);
        }
    }

    private JobParameters buildJobParameters(Path file, Long fileProcessingId) {
        return new JobParametersBuilder()
                .addString("filePath", file.toString(), false)
                .addLong("fileProcessingId", fileProcessingId)
                .addLong("timestamp", System.currentTimeMillis(), false)
                .toJobParameters();
    }

    private void rollbackFileToError(Path processingFile, Long fileProcessingId) {
        if (processingFile == null) {
            return;
        }

        try {
            var errorFile = fileMovementService.moverParaErro(processingFile);

            log.warn("Restart do processamento {} não foi iniciado. Arquivo devolvido para erro: {}",
                    fileProcessingId,
                    errorFile);
        } catch (RuntimeException rollbackException) {
            log.error("Falha ao devolver arquivo para erro após falha no restart do processamento {}. Arquivo: {}",
                    fileProcessingId,
                    processingFile,
                    rollbackException);
        }
    }
}
