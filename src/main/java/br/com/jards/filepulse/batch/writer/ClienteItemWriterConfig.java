package br.com.jards.filepulse.batch.writer;

import javax.sql.DataSource;

import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.jards.filepulse.batch.model.Cliente;

@Configuration
public class ClienteItemWriterConfig {

    @Bean("customerItemWriter")
    public JdbcBatchItemWriter<Cliente> escritorCliente(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Cliente>()
                .dataSource(dataSource)
                .sql(
                    """
                        INSERT INTO customer (
                            file_processing_id,
                            logical_load_id,
                            record_identifier,
                            name,
                            cpf,
                            agency, account,
                            value,
                            reference_month)
                        VALUES (
                            :idProcessamentoArquivo,
                            :idCargaLogica,
                            :idRegistro,
                            :nome,
                            :cpf,
                            :agencia,
                            :conta,
                            :valor,
                            :mesReferencia)
                    """)
                .beanMapped()
                .build();
    }
}
