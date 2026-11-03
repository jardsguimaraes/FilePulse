package br.com.jards.filepulse.batch.reader;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import br.com.jards.filepulse.batch.mapper.ClienteCsvMapper;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;

@Configuration
public class ClienteItemReaderConfig {

    @Bean("customerItemReader")
    @StepScope
    public FlatFileItemReader<RegistroClienteCsv> leitorCliente(
            @Value("#{jobParameters['filePath']}") String caminhoArquivo) {
        return new FlatFileItemReaderBuilder<RegistroClienteCsv>()
                .name("customerItemReader")
                .resource(new FileSystemResource(caminhoArquivo))
                .linesToSkip(1)
                .delimited()
                .delimiter("|")
                .names("name", "cpf", "agency", "account", "value", "referenceMonth")
                .fieldSetMapper(new ClienteCsvMapper())
                .build();
    }
}
