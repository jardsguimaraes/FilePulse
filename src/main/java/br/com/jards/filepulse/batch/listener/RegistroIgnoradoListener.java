package br.com.jards.filepulse.batch.listener;

import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.model.Cliente;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class RegistroIgnoradoListener implements SkipListener<RegistroClienteCsv, Cliente> {

    @Override
    public void onSkipInProcess(RegistroClienteCsv registro, Throwable causa) {
        log.warn("Registro ignorado durante o processamento. Registro: {}. Motivo: {}", registro, causa);
    }
}
