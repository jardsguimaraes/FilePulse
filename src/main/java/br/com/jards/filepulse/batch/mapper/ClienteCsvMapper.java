package br.com.jards.filepulse.batch.mapper;

import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import br.com.jards.filepulse.batch.model.RegistroClienteCsv;

public class ClienteCsvMapper implements FieldSetMapper<RegistroClienteCsv> {

    @Override
    public RegistroClienteCsv mapFieldSet(FieldSet campos) throws BindException {
        return new RegistroClienteCsv(
                campos.readString("name"),
                campos.readRawString("cpf"),
                campos.readString("agency"),
                campos.readString("account"),
                campos.readString("value"),
                campos.readString("referenceMonth").trim(),
                null);
    }

}