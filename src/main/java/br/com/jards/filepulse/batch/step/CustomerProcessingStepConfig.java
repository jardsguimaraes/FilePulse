package br.com.jards.filepulse.batch.step;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

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
    public Step customerProcessingStep() {
        return new StepBuilder("customerProcessingStep", jobRepository)
                .tasklet(
                        (contribution, chunkContext) -> {
                            log.info("CustomerProcessingStep executado com sucesso");
                            return RepeatStatus.FINISHED;
                        }, transactionManager)
                .build();
    }
}
