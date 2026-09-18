package br.com.jards.filepulse.watcher;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.service.FileIdempotencyService;
import br.com.jards.filepulse.batch.service.FileMovementService;
import br.com.jards.filepulse.batch.service.FileStabilityService;
import br.com.jards.filepulse.batch.service.FileValidationService;
import br.com.jards.filepulse.batch.service.JobService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class FileEventHandler {

    private final JobService jobService;
    private final FileValidationService fileValidationService;
    private final FileMovementService fileMovementService;
    private final FileIdempotencyService fileIdempotencyService;
    private final FileStabilityService fileStabilityService;

    public FileEventHandler(FileValidationService fileValidationService, FileMovementService fileMovementService,
            JobService jobService, FileIdempotencyService fileIdempotencyService, FileStabilityService fileStabilityService) {
        this.fileValidationService = fileValidationService;
        this.fileMovementService = fileMovementService;
        this.jobService = jobService;
        this.fileIdempotencyService = fileIdempotencyService;
        this.fileStabilityService = fileStabilityService;
    }

    public void onFileCreated(Path file) {
        log.info("Arquivo detectado: {}", file);

        if (!fileValidationService.isValid(file)) {
            log.warn("Arquivo rejeitado por formato inválido: {}", file);
            fileMovementService.moveToRejected(file);
            return;
        }

        log.info("Arquivo csv válido: {}", file);

        if (!fileStabilityService.waitUntilStable(file)) {
            log.warn("Arquivo não estabilizou após o limite de tentativas: {}", file);
            fileMovementService.moveToRejected(file);
            return;
        }

        if (!fileValidationService.hasValidHeader(file)) {
            log.warn("Arquivo rejeitado por cabeçalho inválido {}", file);
            fileMovementService.moveToRejected(file);
            return;
        }

        fileIdempotencyService.getHashIfNotProcessed(file)
                .ifPresent(fileHash -> {
                    log.info("Arquivo pronto para processamento {}", file);

                    var movedFile = fileMovementService.moveToProcessing(file);
                    var jobStarted = jobService.start(movedFile, fileHash);

                    if (!jobStarted) {
                        fileMovementService.moveToRejected(movedFile);
                    }
                });
    }
}
