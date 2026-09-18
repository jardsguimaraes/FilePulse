package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.exception.FileMovementException;
import br.com.jards.filepulse.watcher.config.FileStorageProperties;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileMovementService {

    private final FileStorageProperties properties;

    public FileMovementService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public Path moveToProcessing(Path file) {
        var processingDirectory = Path.of(properties.getProcessingDirectory());
        var destination = processingDirectory.resolve(file.getFileName());

        try {
            var movedFile = Files.move(file, destination);
            log.info("Arquivo movido para processamento: {}", movedFile);
            return movedFile;
        } catch (IOException e) {
            log.error("Erro ao mover o arquivo {} para {}",
                    file,
                    destination,
                    e);

            throw new FileMovementException(
                    "Não foi possível mover o arquivo para processamento: %s".formatted(file),
                    e);
        }
    }

    public Path moveToProcessed(Path file) {
        try {
            var destination = Path.of(properties.getProcessedDirectory(), file.getFileName().toString());
            var movedFile = Files.move(file, destination);

            log.info("Arquivo movido para processados: {}", movedFile);
            return movedFile;
        } catch (Exception e) {
            throw new FileMovementException("Não foi possível mover o arquivo para processados: %s".formatted(file), e);
        }
    }

    public Path moveToError(Path file) {
        try {
            var destination = Path.of(properties.getErrorDirectory(), file.getFileName().toString());
            var movedFile = Files.move(file, destination);

            log.info("Arquivo movido para erro: {}", movedFile);

            return movedFile;
        } catch (Exception e) {
            throw new FileMovementException("Não foi possível mover o arquivo para erro: %s".formatted(file), e);
        }
    }

    public Path moveToRejected(Path file) {
        try {
            var destination = Path.of(properties.getRejectedDirectory(), file.getFileName().toString());
            var movedFile = Files.move(file, destination);

            log.info("Arquivo movido para rejeitados: {}", movedFile);

            return movedFile;
        } catch (Exception e) {
            throw new FileMovementException("Não foi possível mover o arquivo para rejeitados: %s".formatted(file), e);
        }
    }
}
