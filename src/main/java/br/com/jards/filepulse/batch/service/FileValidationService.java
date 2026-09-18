package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.watcher.config.FileStabilityProperties;

@Service
public class FileValidationService {

    private final FileStabilityProperties properties;
    private static final String EXPECTED_HEADER = "Nome|CPF|Agência|Conta|Valor|Mês de Referência";

    public FileValidationService(FileStabilityProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        return file.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".csv");
    }

    public boolean isFileStable(Path file) {
        try {
            long previousSize = -1;
            int stableChecks = 0;
            int attempts = 0;

            while (stableChecks < properties.getStableChecks() && attempts < properties.getMaxAttempts()) {
                long currentSize = Files.size(file);

                if (currentSize == previousSize) {
                    stableChecks++;
                } else {
                    stableChecks = 0;
                }

                previousSize = currentSize;
                attempts++;
                Thread.sleep(properties.getCheckIntervalMs());
            }

            return stableChecks >= properties.getStableChecks();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean hasValidHeader(Path file) {
        try (var reader = Files.newBufferedReader(file)) {
            var firstLine = reader.readLine();
            return EXPECTED_HEADER.equals(firstLine);
        } catch (IOException e) {
            return false;
        }
    }
}
