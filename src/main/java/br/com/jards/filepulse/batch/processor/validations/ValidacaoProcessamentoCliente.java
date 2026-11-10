package br.com.jards.filepulse.batch.processor.validations;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import br.com.jards.filepulse.batch.exception.RegistroInvalidoException;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;

public class ValidacaoProcessamentoCliente {

    private static final DateTimeFormatter FORMATADOR_MES_REFERENCIA = DateTimeFormatter.ofPattern("MM/yy");

    public void validar(RegistroClienteCsv registro) {

        if (registro.nome().isBlank()) {
            throw new RegistroInvalidoException("Nome do cliente não pode estar vazio");
        }

        if (registro.cpf().isBlank()) {
            throw new RegistroInvalidoException("Cpf do cliente não pode estar vazio");
        }

        if (registro.agencia().isBlank()) {
            throw new RegistroInvalidoException("Agência não pode estar vazia");
        }

        if (registro.conta().isBlank()) {
            throw new RegistroInvalidoException("Conta não pode estar vazia");
        }

        if (registro.valor().isBlank()) {
            throw new RegistroInvalidoException("Valor não pode estar vazio");
        }

        if (registro.mesReferencia().isBlank()) {
            throw new RegistroInvalidoException("Mês não pode estar vazio");
        }
    }

    public BigDecimal converterValor(String valor) {
        try {
            return new BigDecimal(valor);
        } catch (Exception e) {
            throw new RegistroInvalidoException("Valor inválido");
        }
    }

    public void validarMesReferencia(String mesReferencia) {
        try {
            YearMonth.parse(mesReferencia, FORMATADOR_MES_REFERENCIA);
        } catch (DateTimeParseException e) {
            throw new RegistroInvalidoException("Mês de referência inválido");
        }
    }

    public String processarCpf(String cpf) {
        var cpfNormalizado = cpf.replaceAll("\\D", "");

        if ((cpfNormalizado.length() != 11) || (!cpfValido(cpfNormalizado))) {
            throw new RegistroInvalidoException("CPF inválido");
        }

        return cpfNormalizado;
    }

    private boolean cpfValido(String cpf) {
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }

        String cpfBase = cpf.substring(0, 9);

        int primeiroDigito = calcularDigitoCpf(cpfBase, 10);
        int segundoDigito = calcularDigitoCpf(cpfBase + primeiroDigito, 11);

        return cpf.equals(cpfBase + primeiroDigito + segundoDigito);
    }

    private int calcularDigitoCpf(String cpf, int posicao) {
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
