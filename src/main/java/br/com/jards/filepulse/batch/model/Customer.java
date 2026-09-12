package br.com.jards.filepulse.batch.model;

import java.math.BigDecimal;

public record Customer(
        String name,
        String cpf,
        String agency,
        String account,
        BigDecimal value,
        String referenceMonth) {

}
