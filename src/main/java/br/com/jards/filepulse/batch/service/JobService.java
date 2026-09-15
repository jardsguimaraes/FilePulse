package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.exception.JobLaunchException;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class JobService {

    private final JobLauncher jobLauncher;
    private final Job fileProcessJob;
    private final FileProcessingRepository fileProcessingRepository;

    public JobService(JobLauncher jobLauncher, @Qualifier("fileProcessingJob") Job fileProcessJob,
            FileProcessingRepository fileProcessingRepository) {
        this.jobLauncher = jobLauncher;
        this.fileProcessJob = fileProcessJob;
        this.fileProcessingRepository = fileProcessingRepository;
    }

    public void start(Path file, String fileHash) {

        Long fileProcessingId;

        try {
            fileProcessingId = fileProcessingRepository.create(
                    file.getFileName().toString(),
                    file.toString(),
                    fileHash);

        } catch (DuplicateKeyException e) {
            log.warn("Processamento não iniciado. Já existe um arquivo registrado com o hash: {}", fileHash);
            return;
        }

        var parameters = new JobParametersBuilder()
                .addString("filePath", file.toString())
                .addLong("fileProcessingId", fileProcessingId)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        try {
            jobLauncher.run(fileProcessJob, parameters);
        } catch (JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException e) {
            log.error("Erro ao iniciar o Job para o arquivo {}", file, e);
            throw new JobLaunchException("Não foi possível iniciar o Job para o arquivo %s".formatted(file), e);
        }
    }
}
