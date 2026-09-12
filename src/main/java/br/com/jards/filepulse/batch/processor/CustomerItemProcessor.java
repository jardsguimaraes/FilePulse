package br.com.jards.filepulse.batch.processor;

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

        var validation = new CustomerItemProcessorValidation();
        validation.validate(item);
        validation.validateReferenceMonth(item.referenceMonth());

        return new Customer(
                item.name(),
                validation.processCpf(item.cpf()),
                item.agency(),
                item.account(),
                validation.parseValue(item.value()),
                item.referenceMonth());
    }

}
