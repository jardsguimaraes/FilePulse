package br.com.jards.filepulse.batch.writer;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.jards.filepulse.batch.model.Customer;

@Configuration
public class CustomerWriterConfig {

    @Bean
    public JdbcBatchItemWriter<Customer> customerItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Customer>()
        .dataSource(dataSource)
        .sql(
            """
                INSERT INTO customer (file_processing_id, name, cpf, agency, account, value, reference_month)
                    VALUES (:fileProcessingId, :name, :cpf, :agency, :account, :value, :referenceMonth)
            """)
        .beanMapped()
        .build();
    }
}
