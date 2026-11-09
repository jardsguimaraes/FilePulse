package br.com.jards.filepulse.batch.exception;

public class InicializacaoJobException extends RuntimeException {

    public InicializacaoJobException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
