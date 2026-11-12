package br.com.jards.filepulse.batch.model;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;

public record CargaLogica(
        Long id,
        String idCarga,
        long totalRegistros,
        StatusCargaLogica status) {

}
