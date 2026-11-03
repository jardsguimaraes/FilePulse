package br.com.jards.filepulse.batch.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.jards.filepulse.batch.listener.ProcessamentoArquivoJobListener;

@Configuration
public class ProcessamentoArquivoJobConfig {

    private final JobRepository jobRepository;

    public ProcessamentoArquivoJobConfig(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Bean
    public Job processarArquivoJob(@Qualifier("customerProcessingStep") Step processamentoClienteStep,
            ProcessamentoArquivoJobListener processamentoArquivoJobListener) {
        return new JobBuilder("fileProcessingJob", jobRepository)
                .listener(processamentoArquivoJobListener)
                .start(processamentoClienteStep)
                .build();
    }
}
