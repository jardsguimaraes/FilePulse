package br.com.jards.filepulse.batch.reader;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import br.com.jards.filepulse.batch.mapper.customerItemReaderMapper;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;

@Configuration
public class CustomerItemReaderConfig {

    @Bean
    @StepScope
    public FlatFileItemReader<CustomerCsvRecord> customerItemReader(
            @Value("#{jobParameters['filePath']}") String filePath) {
        return new FlatFileItemReaderBuilder<CustomerCsvRecord>()
                .name("customerItemReader")
                .resource(new FileSystemResource(filePath))
                .linesToSkip(1)
                .delimited()
                .delimiter("|")
                .names("name", "cpf", "agency", "account", "value", "referenceMonth")
                .fieldSetMapper(new customerItemReaderMapper())
                .build();
    }
}
