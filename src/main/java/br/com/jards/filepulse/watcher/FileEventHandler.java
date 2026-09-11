package br.com.jards.filepulse.watcher;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.service.FileMovementService;
import br.com.jards.filepulse.service.FileValidationService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class FileEventHandler {

    private final FileValidationService fileValidationService;
    private final FileMovementService fileMovementService;

    public FileEventHandler(FileValidationService fileValidationService, FileMovementService fileMovementService) {
        this.fileValidationService = fileValidationService;
        this.fileMovementService = fileMovementService;
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

        log.info("Arquivo pronto para processamento {}", file);

        fileMovementService.moveToProcessing(file);
    }
}
