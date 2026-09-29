package br.com.jards.filepulse.batch.processor;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.model.Customer;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;
import br.com.jards.filepulse.batch.processor.validations.CustomerItemProcessorValidation;

@Component
@StepScope
public class CustomerItemProcessor implements ItemProcessor<CustomerCsvRecord, Customer> {

    private final Long fileProcessingId;

    public CustomerItemProcessor(@Value("#{jobParameters['fileProcessingId']}") Long fileProcessingId) {
        this.fileProcessingId = fileProcessingId;
    }

    @Override
    @Nullable
    public Customer process(@NonNull CustomerCsvRecord item) throws Exception {

        var validation = new CustomerItemProcessorValidation();
        validation.validate(item);
        validation.validateReferenceMonth(item.referenceMonth());

        return new Customer(
                fileProcessingId,
                item.name(),
                validation.processCpf(item.cpf()),
                item.agency(),
                item.account(),
                validation.parseValue(item.value()),
                item.referenceMonth());
    }

}
