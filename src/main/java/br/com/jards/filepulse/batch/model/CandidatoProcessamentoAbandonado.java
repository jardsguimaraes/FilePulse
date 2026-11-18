package br.com.jards.filepulse.batch.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

public record CandidatoProcessamentoAbandonado(
                Long idProcessamentoArquivo,
                UUID idTentativa,
                Path arquivo,
                Path arquivoOrigemTentativa,
                LocalDateTime iniciadoEm,
                UUID idInstancia) {

}
