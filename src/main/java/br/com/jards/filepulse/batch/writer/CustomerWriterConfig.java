package br.com.jards.filepulse.batch.writer;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.jards.filepulse.batch.model.Cliente;

@Configuration
public class CustomerWriterConfig {

    @Bean
    public JdbcBatchItemWriter<Cliente> customerItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Cliente>()
                .dataSource(dataSource)
                .sql(
                        """
                                    INSERT INTO customer (file_processing_id, name, cpf, agency, account, value, reference_month)
                                        VALUES (:id, :nome, :cpf, :agencia, :conta, :valor, :mesReferencia)
                                """)
                .beanMapped()
                .build();
    }
}
