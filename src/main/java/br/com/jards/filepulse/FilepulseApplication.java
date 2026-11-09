package br.com.jards.filepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import br.com.jards.filepulse.batch.config.RecuperacaoProcessamentoArquivoProperties;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;
import br.com.jards.filepulse.watcher.config.EstabilidadeArquivoProperties;

@SpringBootApplication
@EnableConfigurationProperties({
		ArmazenamentoArquivoProperties.class,
		EstabilidadeArquivoProperties.class,
		RecuperacaoProcessamentoArquivoProperties.class })
public class FilepulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(FilepulseApplication.class, args);
	}
}
