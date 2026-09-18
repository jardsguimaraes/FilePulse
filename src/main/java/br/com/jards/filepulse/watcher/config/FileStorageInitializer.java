package br.com.jards.filepulse.watcher.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class FileStorageInitializer {

    private final FileStorageProperties properties;

    public FileStorageInitializer(FileStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(Path.of(properties.getInputDirectory()));
        Files.createDirectories(Path.of(properties.getProcessingDirectory()));
        Files.createDirectories(Path.of(properties.getProcessedDirectory()));
        Files.createDirectories(Path.of(properties.getErrorDirectory()));
        Files.createDirectories(Path.of(properties.getRejectedDirectory()));
    }
}
