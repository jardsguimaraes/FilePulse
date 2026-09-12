package br.com.jards.filepulse.batch.mapper;

import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import br.com.jards.filepulse.batch.model.CustomerCsvRecord;

public class CustomerItemReaderMapper implements FieldSetMapper<CustomerCsvRecord> {

    @Override
    public CustomerCsvRecord mapFieldSet(FieldSet fieldSet) throws BindException {
        return new CustomerCsvRecord(
                fieldSet.readString("name").trim(),
                fieldSet.readString("cpf").trim(),
                fieldSet.readString("agency").trim(),
                fieldSet.readString("account").trim(),
                fieldSet.readString("value").trim(),
                fieldSet.readString("referenceMonth").trim());
    }

}