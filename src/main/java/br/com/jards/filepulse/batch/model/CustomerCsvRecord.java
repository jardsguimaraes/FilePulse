package br.com.jards.filepulse.batch.model;

public record CustomerCsvRecord(
    String name,
    String cpf,
    String agency,
    String account,
    String value,
    String referenceMonth) {
}