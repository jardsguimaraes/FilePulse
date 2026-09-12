package br.com.jards.filepulse.batch.processor;

import java.math.BigDecimal;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.model.Customer;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;
import br.com.jards.filepulse.batch.processor.validations.CustomerItemProcessorValidation;

@Component
public class CustomerItemProcessor implements ItemProcessor<CustomerCsvRecord, Customer> {

    @Override
    @Nullable
    public Customer process(@NonNull CustomerCsvRecord item) throws Exception {

        CustomerItemProcessorValidation.validate(item);

        return new Customer(
                item.name(),
                normalizeCpf(item.cpf()),
                item.agency(),
                item.account(),
                new BigDecimal(item.value()),
                item.referenceMonth());
    }

    private String normalizeCpf(String cpf) {
        return cpf.replaceAll("\\D", "");
    }

}
