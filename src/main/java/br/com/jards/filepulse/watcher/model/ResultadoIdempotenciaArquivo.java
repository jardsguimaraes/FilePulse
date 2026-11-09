package br.com.jards.filepulse.watcher.model;

public record ResultadoIdempotenciaArquivo(
                String hashArquivo,
                boolean duplicado) {

}
