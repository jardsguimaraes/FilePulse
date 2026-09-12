package br.com.jards.filepulse.batch.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileProcessingJobConfig {

    private final JobRepository jobRepository;

    public FileProcessingJobConfig(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Bean
    public Job fileProcessingJob(@Qualifier("customerProcessingStep") Step customerProcessingStep) {
        return new JobBuilder("fileProcessingJob", jobRepository)
                .start(customerProcessingStep)
                .build();
    }
}
