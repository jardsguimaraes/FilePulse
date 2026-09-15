package br.com.jards.filepulse.watcher;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.service.FileIdempotencyService;
import br.com.jards.filepulse.batch.service.FileMovementService;
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

    public FileEventHandler(FileValidationService fileValidationService, FileMovementService fileMovementService,
            JobService jobService, FileIdempotencyService fileIdempotencyService) {
        this.fileValidationService = fileValidationService;
        this.fileMovementService = fileMovementService;
        this.jobService = jobService;
        this.fileIdempotencyService = fileIdempotencyService;
    }

    public void onFileCreated(Path file) {
        log.info("Arquivo detectado: {}", file);

        if (!fileValidationService.isValid(file)) {
            log.warn("Arquivo ignorado: {}", file);
            return;
        }

        log.info("Arquivo csv válido: {}", file);

        if (!fileValidationService.isFileStable(file)) {
            log.warn("Arquivo ainda não está estável: {}", file);
            return;
        }

        if (!fileValidationService.hasValidHeader(file)) {
            log.warn("Arquivo com cabeçalho inválido {}", file);
            return;
        }

        fileIdempotencyService.getHashIfNotProcessed(file)
                .ifPresent(fileHash -> {
                    log.info("Arquivo pronto para processamento {}", file);

                    var movedFile = fileMovementService.moveToProcessing(file);
                    jobService.start(movedFile, fileHash);
                });
    }
}
