package br.com.jards.filepulse.batch.listener;

import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.model.Cliente;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;
import br.com.jards.filepulse.batch.repository.ProcessamentoErroRepository;
import lombok.extern.log4j.Log4j2;

@Component
@StepScope
@Log4j2
public class RegistroIgnoradoListener implements SkipListener<RegistroClienteCsv, Cliente> {

    private final ProcessamentoErroRepository processamentoErroRepository;
    private final Long idProcessamentoArquivo;

    public RegistroIgnoradoListener(ProcessamentoErroRepository processamentoErroRepository,
            @Value("#{jobParameters['fileProcessingId']}") Long idProcessamentoArquivo) {
        this.processamentoErroRepository = processamentoErroRepository;
        this.idProcessamentoArquivo = idProcessamentoArquivo;
    }

    @Override
    public void onSkipInProcess(RegistroClienteCsv registro, Throwable causa) {

        processamentoErroRepository.registrar(
                idProcessamentoArquivo,
                registro.numeroLinha(),
                "PROCESSAMENTO",
                causa.getClass().getSimpleName(),
                causa.getMessage(),
                null);

        log.warn("Registro ignorado. Processamento: {}. Linha: {}. Motivo: {}",
                idProcessamentoArquivo,
                registro.numeroLinha(),
                causa.getMessage());
    }
}
