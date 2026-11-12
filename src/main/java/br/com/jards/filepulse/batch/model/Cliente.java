package br.com.jards.filepulse.batch.model;

import java.math.BigDecimal;

public record Cliente(
        Long idProcessamentoArquivo,
        Long idCargaLogica,
        String idRegistro,
        String nome,
        String cpf,
        String agencia,
        String conta,
        BigDecimal valor,
        String mesReferencia) {

}