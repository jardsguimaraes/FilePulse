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
        public void beforeJob(JobExecution jobExecution) {
                var fileProcessingId = jobExecution.getJobParameters()
                                .getLong("fileProcessingId");

                var filePath = jobExecution.getJobParameters()
                                .getString("filePath");

                var updated = fileProcessingRepository.markAsProcessing(fileProcessingId, filePath);

                if (!updated) {
                        throw new IllegalStateException("Não foi possível iniciar o processamento %d"
                                        .formatted(fileProcessingId));
                }
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

                var filePath = jobExecution.getJobParameters().getString("filePath");
                Path finalFile;

                if (status == FileProcessingStatus.COMPLETED) {
                        finalFile = fileMovementService.moveToProcessed(Path.of(filePath));
                } else {
                        finalFile = fileMovementService.moveToError(Path.of(filePath));
                }

                fileProcessingRepository.finish(fileProcessingId, status, readCount, writeCount, skipCount,
                                errorMessage, finalFile.toString());

        }
}
