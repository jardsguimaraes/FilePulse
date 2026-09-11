package br.com.jards.filepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import br.com.jards.filepulse.config.FileStorageProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileStorageProperties.class)
public class FilepulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(FilepulseApplication.class, args);
	}
}
