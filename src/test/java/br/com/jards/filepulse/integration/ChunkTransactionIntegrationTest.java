package br.com.jards.filepulse.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.jards.filepulse.FilepulseApplication;

@SpringBootTest(classes = {
        FilepulseApplication.class,
        ChunkTransactionIntegrationTest.BatchTestConfig.class
})
@Testcontainers
class ChunkTransactionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    Job chunkTransactionTestJob;

    @Test
    void shouldKeepCommittedChunksWhenLaterChunkFails() throws Exception {

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(chunkTransactionTestJob, jobParameters);

        assertEquals(BatchStatus.FAILED, execution.getStatus());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk_test",
                Integer.class);

        assertEquals(30, count);

        List<Integer> persistedIds = jdbcTemplate.queryForList(
                "SELECT id FROM chunk_test ORDER BY id",
                Integer.class);

        List<Integer> expectedIds = IntStream.rangeClosed(1, 30)
                .boxed()
                .toList();

        assertEquals(expectedIds, persistedIds);
    }

    @TestConfiguration
    static class BatchTestConfig {

        @Autowired
        JobRepository jobRepository;

        @Bean
        CommandLineRunner createChunkTestTable(JdbcTemplate jdbcTemplate) {
            return args -> jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS chunk_test (
                        id INTEGER PRIMARY KEY,
                        value VARCHAR(100) NOT NULL
                    )
                    """);
        }

        @Bean
        ListItemReader<Integer> chunkTestReader() {
            return new ListItemReader<>(
                    IntStream.rangeClosed(1, 40)
                            .boxed()
                            .toList());
        }

        @Bean
        ItemProcessor<Integer, Integer> chunkTestProcessor() {
            return item -> {
                if (item == 31) {
                    throw new RuntimeException(
                            "Falha controlada no registro 31");
                }

                return item;
            };
        }

        @Bean
        ItemWriter<Integer> chunkTestWriter(JdbcTemplate jdbcTemplate) {
            return chunk -> {
                for (Integer item : chunk.getItems()) {
                    jdbcTemplate.update(
                            "INSERT INTO chunk_test (id, value) VALUES (?, ?)",
                            item,
                            "Registro " + item);
                }
            };
        }

        @Bean
        Step chunkTransactionTestStep(
                PlatformTransactionManager transactionManager,
                ListItemReader<Integer> chunkTestReader,
                ItemProcessor<Integer, Integer> chunkTestProcessor,
                ItemWriter<Integer> chunkTestWriter) {

            return new StepBuilder("chunkTransactionTestStep", jobRepository)
                    .<Integer, Integer>chunk(10, transactionManager)
                    .reader(chunkTestReader)
                    .processor(chunkTestProcessor)
                    .writer(chunkTestWriter)
                    .build();
        }

        @Bean
        Job chunkTransactionTestJob(
                Step chunkTransactionTestStep) {

            return new JobBuilder("chunkTransactionTestJob", jobRepository)
                    .start(chunkTransactionTestStep)
                    .build();
        }
    }
}