package br.com.jards.filepulse.batch.model;

import java.util.UUID;

public record ReservaProcessamento(
        Long idProcessamentoArquivo,
        Long idCargaLogica,
        UUID idTentativa) {

}
