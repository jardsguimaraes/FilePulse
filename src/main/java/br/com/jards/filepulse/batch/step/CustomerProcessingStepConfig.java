package br.com.jards.filepulse.batch.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.jards.filepulse.batch.exception.InvalidRecordException;
import br.com.jards.filepulse.batch.listener.RecordSkipListener;
import br.com.jards.filepulse.batch.model.Customer;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;
import br.com.jards.filepulse.batch.processor.CustomerItemProcessor;
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
            @Qualifier("customerItemReader") FlatFileItemReader<CustomerCsvRecord> customerItemReader,
            CustomerItemProcessor customerItemProcessor, RecordSkipListener recordSkipListener,
            JdbcBatchItemWriter<Customer> customerItemWriter) {
        return new StepBuilder("customerProcessingStep", jobRepository)
                .<CustomerCsvRecord, Customer>chunk(10, transactionManager)
                .reader(customerItemReader)
                .processor(customerItemProcessor)
                .faultTolerant()
                .skip(InvalidRecordException.class)
                .skipLimit(20)
                .listener(recordSkipListener)
                .writer(customerItemWriter)
                .build();
    }
}
