package br.com.jards.filepulse.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

@Service
public class FileValidationService {

    private static final String EXPECTED_HEADER = "Nome|CPF|Agência|Conta|Valor|Mês de Referência";

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

            while (stableChecks < 3) {
                long currentSize = Files.size(file);

                if (currentSize == previousSize) {
                    stableChecks++;
                }

                previousSize = currentSize;
                Thread.sleep(1000);
            }

            return true;
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
