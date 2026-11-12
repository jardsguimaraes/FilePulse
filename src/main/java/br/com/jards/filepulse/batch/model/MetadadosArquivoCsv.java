package br.com.jards.filepulse.batch.model;

import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;

public record MetadadosArquivoCsv(
        String idCarga,
        TipoArquivoCsv tipoArquivo,
        long totalRegistros) {

}