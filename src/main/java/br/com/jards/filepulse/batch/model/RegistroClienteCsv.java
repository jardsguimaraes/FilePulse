package br.com.jards.filepulse.batch.model;

public record RegistroClienteCsv(
                String nome,
                String cpf,
                String agencia,
                String conta,
                String valor,
                String mesReferencia,
                Long numeroLinha) {
}