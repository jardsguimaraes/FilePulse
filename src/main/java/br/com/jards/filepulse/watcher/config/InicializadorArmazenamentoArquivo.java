package br.com.jards.filepulse.watcher.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class InicializadorArmazenamentoArquivo {

    private final ArmazenamentoArquivoProperties propriedadesArmazenamento;

    public InicializadorArmazenamentoArquivo(ArmazenamentoArquivoProperties propriedadesArmazenamento) {
        this.propriedadesArmazenamento = propriedadesArmazenamento;
    }

    @PostConstruct
    public void inicializar() throws IOException {
        Files.createDirectories(Path.of(propriedadesArmazenamento.getDiretorioEntrada()));
        Files.createDirectories(Path.of(propriedadesArmazenamento.getDiretorioProcessamento()));
        Files.createDirectories(Path.of(propriedadesArmazenamento.getDiretorioProcessados()));
        Files.createDirectories(Path.of(propriedadesArmazenamento.getDiretorioErro()));
        Files.createDirectories(Path.of(propriedadesArmazenamento.getDiretorioRejeitados()));
    }
}
