package br.com.jards.filepulse.batch.reader;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import br.com.jards.filepulse.batch.mapper.ClienteCsvMapper;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;

@Configuration
public class ClienteItemReaderConfig {

        @Bean("customerItemReader")
        @StepScope
        public FlatFileItemReader<RegistroClienteCsv> leitorCliente(
                        @Value("#{jobParameters['filePath']}") String caminhoArquivo) {
                var tokenizer = new DelimitedLineTokenizer("|");
                tokenizer.setNames(
                                "idRegistro",
                                "name",
                                "cpf",
                                "agency",
                                "account",
                                "value",
                                "referenceMonth");

                var mapper = new ClienteCsvMapper();

                return new FlatFileItemReaderBuilder<RegistroClienteCsv>()
                                .name("customerItemReader")
                                .resource(new FileSystemResource(caminhoArquivo))
                                .linesToSkip(4)
                                .lineMapper((linha, numero) -> {
                                        var campos = tokenizer.tokenize(linha);
                                        var registro = mapper.mapFieldSet(campos);

                                        return new RegistroClienteCsv(
                                                        registro.idRegistro(),
                                                        registro.nome(),
                                                        registro.cpf(),
                                                        registro.agencia(),
                                                        registro.conta(),
                                                        registro.valor(),
                                                        registro.mesReferencia(),
                                                        (long) numero);
                                })
                                .build();
        }
}
