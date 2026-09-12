package br.com.jards.filepulse.batch.processor.validations;

import br.com.jards.filepulse.batch.model.CustomerCsvRecord;
import br.com.jards.filepulse.exception.InvalidRecordException;

public class CustomerItemProcessorValidation {

    public static void validate(CustomerCsvRecord item) {

        if (item.name().isBlank()) {
            throw new InvalidRecordException("Nome do cliente não pode estar vazio");
        }

        if (item.cpf().isBlank()) {
            throw new InvalidRecordException("Cpf do cliente não pode estar vazio");
        }

        if (item.agency().isBlank()) {
            throw new InvalidRecordException("Agência não pode estar vazia");
        }

        if (item.account().isBlank()) { 
            throw new InvalidRecordException("Conta não pode estar vazia");
        }

        if (item.value().isBlank()) {
            throw new InvalidRecordException("Valor não pode estar vazio");
        }

        if (item.referenceMonth().isBlank()) {
            throw new InvalidRecordException("Mês não pode estar vazio");
        }
    }
}
