package br.com.jards.filepulse.batch.writer;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.jards.filepulse.batch.model.Cliente;
import br.com.jards.filepulse.batch.service.AutorizacaoProcessamentoService;

@Configuration
public class ClienteItemWriterConfig {

    private final AutorizacaoProcessamentoService autorizacaoProcessamentoService;

    public ClienteItemWriterConfig(AutorizacaoProcessamentoService autorizacaoProcessamentoService) {
        this.autorizacaoProcessamentoService = autorizacaoProcessamentoService;
    }

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

    @Bean("customerItemWriterProtegido")
    @StepScope
    public ItemWriter<Cliente> escritorClienteProtegido(
            @Qualifier("customerItemWriter") JdbcBatchItemWriter<Cliente> escritorCliente,
            @Value("#{jobParameters['fileProcessingId']}") Long idProcessamentoArquivo,
            @Value("#{jobParameters['attemptId']}") String tentativa) {

        return chunk -> {
            UUID idTentativa = UUID.fromString(tentativa);
            autorizacaoProcessamentoService.validarAutorizacao(
                    idProcessamentoArquivo,
                    idTentativa);
            escritorCliente.write(chunk);
        };
    }

}
