package br.com.jards.filepulse.batch.processor.validations;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import br.com.jards.filepulse.batch.exception.InvalidRecordException;
import br.com.jards.filepulse.batch.model.CustomerCsvRecord;

public class CustomerItemProcessorValidation {

    private static final DateTimeFormatter REFERENCE_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MM/yy");

    public void validate(CustomerCsvRecord item) {

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

    public BigDecimal parseValue(String value) {
        try {
            return new BigDecimal(value);
        } catch (Exception e) {
            throw new InvalidRecordException("Valor inválido: %s".formatted(value));
        }
    }

    public void validateReferenceMonth(String referenceMonth) {
        try {
            YearMonth.parse(referenceMonth, REFERENCE_MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidRecordException("Mês de referência inválido: %s".formatted(referenceMonth));
        }
    }

    public String processCpf(String cpf) {
        var norrmalizedCpf = cpf.replaceAll("\\D", "");

        if ((norrmalizedCpf.length() != 11)  || (!isValidCpf(norrmalizedCpf))){
            throw new InvalidRecordException("CPF inválido: %s".formatted(cpf));
        }

        return norrmalizedCpf;
    }

    private boolean isValidCpf(String cpf) {
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }

        int firstDigit = calculateCpfDigit(cpf.substring(0, 9), 10);
        int secondDigit = calculateCpfDigit(cpf.substring(0, 9), 11);

        return cpf.equals(cpf.substring(0, 9) + firstDigit + secondDigit);
    }

    private int calculateCpfDigit(String cpf, int posicao) {
        int soma = 0;

        for (char digito : cpf.toCharArray()) {
            soma += Character.getNumericValue(digito) * posicao;
            posicao--;
        }

        int resto = soma % 11;

        return resto < 2
                ? 0
                : 11 - resto;
    }
}
