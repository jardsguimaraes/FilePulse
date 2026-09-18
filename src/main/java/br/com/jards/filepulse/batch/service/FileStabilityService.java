package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.watcher.config.FileStabilityProperties;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileStabilityService {

    private final FileValidationService fileValidationService;
    private final FileStabilityProperties properties;

    public FileStabilityService(FileValidationService fileValidationService, FileStabilityProperties properties) {
        this.fileValidationService = fileValidationService;
        this.properties = properties;
    }

    public boolean waitUntilStable(Path file) {
        for (int retry = 0; retry <= properties.getMaxRetries(); retry++) {
            if (fileValidationService.isFileStable(file)) {
                return true;
            }

            if (retry < properties.getMaxRetries()) {
                log.warn("Arquivo ainda não estabilizou. Nova tentativa será realizada. Arquivo: {}, tentativa: {}/{}",
                        file, retry + 1, properties.getMaxRetries());

                try {
                    Thread.sleep(properties.getRetryIntervalMs());
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return  false;
    }
}
