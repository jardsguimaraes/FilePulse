package br.com.jards.filepulse.watcher.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class FileStorageInitializer {

    private final ArmazenamentoArquivoProperties properties;

    public FileStorageInitializer(ArmazenamentoArquivoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(Path.of(properties.getDiretorioEntrada()));
        Files.createDirectories(Path.of(properties.getDiretorioProcessamento()));
        Files.createDirectories(Path.of(properties.getDiretorioProcessados()));
        Files.createDirectories(Path.of(properties.getDiretorioErro()));
        Files.createDirectories(Path.of(properties.getDiretorioRejeitados()));
    }
}
