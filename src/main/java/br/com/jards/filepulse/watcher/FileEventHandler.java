package br.com.jards.filepulse.watcher;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.service.FileIdempotencyService;
import br.com.jards.filepulse.batch.service.FileMovementService;
import br.com.jards.filepulse.batch.service.FileStabilityService;
import br.com.jards.filepulse.batch.service.FileValidationService;
import br.com.jards.filepulse.batch.service.JobService;
import br.com.jards.filepulse.watcher.enums.FileRejectionReason;
import br.com.jards.filepulse.watcher.service.FileRejectionService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class FileEventHandler {

    private final JobService jobService;
    private final FileValidationService fileValidationService;
    private final FileMovementService fileMovementService;
    private final FileIdempotencyService fileIdempotencyService;
    private final FileStabilityService fileStabilityService;
    private final FileRejectionService fileRejectionService;

    public FileEventHandler(FileValidationService fileValidationService, FileMovementService fileMovementService,
            JobService jobService, FileIdempotencyService fileIdempotencyService,
            FileStabilityService fileStabilityService, FileRejectionService fileRejectionService) {
        this.fileValidationService = fileValidationService;
        this.fileMovementService = fileMovementService;
        this.jobService = jobService;
        this.fileIdempotencyService = fileIdempotencyService;
        this.fileStabilityService = fileStabilityService;
        this.fileRejectionService = fileRejectionService;
    }

    public void onFileCreated(Path file) {
        log.info("Arquivo detectado: {}", file);

        if (!fileValidationService.isValid(file)) {
            fileRejectionService.reject(file, null, FileRejectionReason.INVALID_FORMAT, "Formato de arquivo inválido");
            return;
        }

        log.info("Arquivo csv válido: {}", file);

        if (!fileStabilityService.waitUntilStable(file)) {
            fileRejectionService.reject(file, null, FileRejectionReason.UNSTABLE_FILE,
                    "Arquivo não estabilizou após o limite de tentativas");
            return;
        }

        if (!fileValidationService.hasValidHeader(file)) {
            fileRejectionService.reject(file, null, FileRejectionReason.INVALID_HEADER,
                    "Cabeçalho do arquivo inválido");
            return;
        }

        var idempotencyResult = fileIdempotencyService.check(file);

        if (idempotencyResult.duplicate()) {
            fileRejectionService.reject(file, idempotencyResult.fileHash(), FileRejectionReason.DUPLICATE,
                    "Arquivo duplicado detectado antes do processamento");

            return;
        }

        var fileHash = idempotencyResult.fileHash();

        log.info("Arquivo pronto para processamento {}", file);

        var movedFile = fileMovementService.moveToProcessing(file);
        var jobStarted = jobService.start(movedFile, fileHash);

        if (!jobStarted) {
            fileRejectionService.reject(movedFile, fileHash, FileRejectionReason.DUPLICATE,
                    "Arquivo duplicado detectado durante o início do processamento");
        }
    }
}
