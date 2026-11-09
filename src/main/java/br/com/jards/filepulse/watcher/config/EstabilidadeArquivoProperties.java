package br.com.jards.filepulse.watcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "fileprocessor.stability")
public class EstabilidadeArquivoProperties {

    private int verificacoesEstabilidade;
    private int maximoTentativas;
    private long intervaloVerificacaoMs;
    private int maximoRetentativas;
    private long intervaloRetentativaMs;
}
