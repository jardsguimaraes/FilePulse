package br.com.jards.filepulse.batch.listener;

import java.nio.file.Path;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.enums.FileProcessingStatus;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;
import br.com.jards.filepulse.batch.service.FileMovementService;

@Component
public class FileProcessingJobListener implements JobExecutionListener {

        private final FileProcessingRepository fileProcessingRepository;
        private final FileMovementService fileMovementService;

        public FileProcessingJobListener(FileProcessingRepository fileProcessingRepository,
                        FileMovementService fileMovementService) {
                this.fileProcessingRepository = fileProcessingRepository;
                this.fileMovementService = fileMovementService;
        }

        @Override
        public void afterJob(JobExecution jobExecution) {
                var fileProcessingId = jobExecution.getJobParameters()
                                .getLong("fileProcessingId");

                var readCount = jobExecution.getStepExecutions().stream()
                                .mapToLong(step -> step.getReadCount())
                                .sum();

                var writeCount = jobExecution.getStepExecutions().stream()
                                .mapToLong(step -> step.getWriteCount())
                                .sum();

                var skipCount = jobExecution.getStepExecutions().stream()
                                .mapToLong(step -> step.getReadSkipCount()
                                                + step.getProcessSkipCount()
                                                + step.getWriteSkipCount())
                                .sum();

                var status = jobExecution.getStatus() == BatchStatus.COMPLETED
                                ? FileProcessingStatus.COMPLETED
                                : FileProcessingStatus.FAILED;

                String errorMessage = null;
                if (status == FileProcessingStatus.FAILED) {
                        errorMessage = jobExecution.getAllFailureExceptions().stream()
                                        .map(Throwable::getMessage)
                                        .filter(message -> message != null && !message.isBlank())
                                        .findFirst()
                                        .orElse("Falha durante o processamento do arquivo");
                }

                fileProcessingRepository.finish(fileProcessingId, status, readCount, writeCount, skipCount,
                                errorMessage);

                var filePath = jobExecution.getJobParameters().getString("filePath");

                if (status == FileProcessingStatus.COMPLETED) {
                        fileMovementService.moveToProcessed(Path.of(filePath));
                } else if (status == FileProcessingStatus.FAILED) {
                        fileMovementService.moveToError(Path.of(filePath));
                }
        }
}
