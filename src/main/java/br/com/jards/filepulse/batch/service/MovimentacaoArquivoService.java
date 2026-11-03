package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.exception.MovimentacaoArquivoException;
import br.com.jards.filepulse.watcher.config.FileStorageProperties;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class MovimentacaoArquivoService {

    private final FileStorageProperties properties;

    public MovimentacaoArquivoService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public Path moveToProcessing(Path file) {
        try {
            var movedFile = move(file, Path.of(properties.getProcessingDirectory()));
            log.info("Arquivo movido para processamento: {}", movedFile);
            return movedFile;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException(
                    "Não foi possível mover o arquivo para processamento: %s".formatted(file),
                    e);
        }
    }

    public Path moverParaProcessados(Path file) {
        try {
            var movedFile = move(file, Path.of(properties.getProcessedDirectory()));

            log.info("Arquivo movido para processados: {}", movedFile);
            return movedFile;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException(
                    "Não foi possível mover o arquivo para processados: %s".formatted(file), e);
        }
    }

    public Path moverParaErro(Path file) {
        try {
            var movedFile = move(file, Path.of(properties.getErrorDirectory()));

            log.info("Arquivo movido para erro: {}", movedFile);

            return movedFile;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException("Não foi possível mover o arquivo para erro: %s".formatted(file), e);
        }
    }

    public Path moveToRejected(Path file) {
        try {
            var movedFile = move(file, Path.of(properties.getRejectedDirectory()));

            log.info("Arquivo movido para rejeitados: {}", movedFile);

            return movedFile;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException(
                    "Não foi possível mover o arquivo para rejeitados: %s".formatted(file), e);
        }
    }

    private Path resolveDestination(Path directory, Path file) {
        var destination = directory.resolve(file.getFileName());

        if (!Files.exists(destination)) {
            return destination;
        }

        return createUniqueDestination(directory, file);
    }

    private Path move(Path file, Path directory) throws IOException {
        var destination = resolveDestination(directory, file);

        try {
            return Files.move(file, destination);
        } catch (FileAlreadyExistsException e) {
            var uniqueDestination = createUniqueDestination(directory, file);
            return Files.move(file, uniqueDestination);
        }
    }

    private Path createUniqueDestination(Path directory, Path file) {
        var fileName = file.getFileName().toString();
        var dotIndex = fileName.lastIndexOf('.');

        var baseName = dotIndex > 0
                ? fileName.substring(0, dotIndex)
                : fileName;

        var extension = dotIndex > 0
                ? fileName.substring(dotIndex)
                : "";

        var uniqueFileName = "%s_%s%s".formatted(baseName, UUID.randomUUID(), extension);

        return directory.resolve(uniqueFileName);
    }
}
