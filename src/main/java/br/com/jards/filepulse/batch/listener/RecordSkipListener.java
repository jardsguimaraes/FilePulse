package br.com.jards.filepulse.batch.listener;

import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.model.Customer;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class RecordSkipListener implements SkipListener<CustomerCsvRecord, Customer> {

    @Override
    public void onSkipInProcess(CustomerCsvRecord item, Throwable cause) {
        log.warn("Registro ignorado durante o processamento. Registro: {}. Motivo: {}", item, cause);
    }
}
