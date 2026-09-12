package br.com.jards.filepulse.batch.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.jards.filepulse.batch.model.CustomerCsvRecord;
import lombok.extern.log4j.Log4j2;

@Configuration
@Log4j2
public class CustomerProcessingStepConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    public CustomerProcessingStepConfig(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
    }

    @Bean
    public Step customerProcessingStep(
            @Qualifier("customerItemReader") FlatFileItemReader<CustomerCsvRecord> customerItemReader) {
        return new StepBuilder("customerProcessingStep", jobRepository)
                .<CustomerCsvRecord, CustomerCsvRecord>chunk(10, transactionManager)
                .reader(customerItemReader)
                .writer(chuck -> {
                    chuck.getItems()
                            .forEach(item -> log.info("Registro lido: {}", item));
                })
                .build();
    }
}
