package br.com.jards.filepulse.watcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "fileprocessor.directory")
public class ArmazenamentoArquivoProperties {

    private String diretorioEntrada;
    private String diretorioProcessamento;
    private String diretorioProcessados;
    private String diretorioErro;
    private String diretorioRejeitados;
}
