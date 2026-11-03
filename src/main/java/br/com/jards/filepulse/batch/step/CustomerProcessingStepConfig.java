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

import br.com.jards.filepulse.batch.exception.RegistroInvalidoException;
import br.com.jards.filepulse.batch.listener.RegistroIgnoradoListener;
import br.com.jards.filepulse.batch.model.Cliente;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;
import br.com.jards.filepulse.batch.processor.ClienteItemProcessor;
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

    @Bean("customerProcessingStep")
    public Step etapaProcessamentoClientes(
            @Qualifier("customerItemReader") FlatFileItemReader<RegistroClienteCsv> clienteItemReader,
            ClienteItemProcessor clienteItemProcessor, RegistroIgnoradoListener listenerRegistroIgnorado,
            JdbcBatchItemWriter<Cliente> clienteItemWriter) {
        return new StepBuilder("etapaProcessamentoClientes", jobRepository)
                .<RegistroClienteCsv, Cliente>chunk(10, transactionManager)
                .reader(clienteItemReader)
                .processor(clienteItemProcessor)
                .faultTolerant()
                .skip(RegistroInvalidoException.class)
                .skipLimit(20)
                .listener(listenerRegistroIgnorado)
                .writer(clienteItemWriter)
                .build();
    }
}
