package br.com.jards.filepulse.exception;

public class FileMovementException extends RuntimeException {

    public FileMovementException(String message, Throwable couse) {
        super(message, couse);
    }
}
