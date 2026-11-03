package br.com.jards.filepulse.batch.exception;

public class MovimentacaoArquivoException extends RuntimeException {

    public MovimentacaoArquivoException(String message, Throwable couse) {
        super(message, couse);
    }
}
