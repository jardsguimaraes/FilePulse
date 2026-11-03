package br.com.jards.filepulse.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ConfigurationProperties(prefix = "fileprocessor.recovery")
public class RecuperacaoProcessamentoArquivoProperties {

    private Long abandonedAfterMinutes;
}
