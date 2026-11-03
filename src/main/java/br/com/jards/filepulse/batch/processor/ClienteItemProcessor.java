package br.com.jards.filepulse.batch.processor;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.model.Cliente;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;
import br.com.jards.filepulse.batch.processor.validations.ValidacaoProcessamentoCliente;

@Component
@StepScope
public class ClienteItemProcessor implements ItemProcessor<RegistroClienteCsv, Cliente> {

    private final Long id;

    public ClienteItemProcessor(@Value("#{jobParameters['fileProcessingId']}") Long fileProcessingId) {
        this.id = fileProcessingId;
    }

    @Override
    @Nullable
    public Cliente process(@NonNull RegistroClienteCsv registro) throws Exception {

        var validacao = new ValidacaoProcessamentoCliente();
        validacao.validar(registro);
        validacao.validarMesReferencia(registro.mesReferencia());

        return new Cliente(
                id,
                registro.nome(),
                validacao.processarCpf(registro.cpf()),
                registro.agencia(),
                registro.conta(),
                validacao.converterValor(registro.valor()),
                registro.mesReferencia());
    }

}
