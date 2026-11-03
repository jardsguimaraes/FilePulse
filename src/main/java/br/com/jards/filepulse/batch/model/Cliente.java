package br.com.jards.filepulse.batch.model;

import java.math.BigDecimal;

public record Cliente(
        Long id,
        String nome,
        String cpf,
        String agencia,
        String conta,
        BigDecimal valor,
        String mesReferencia) {

}