package br.com.jards.filepulse.batch.exception;

public class MovimentacaoArquivoException extends RuntimeException {

    public MovimentacaoArquivoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
