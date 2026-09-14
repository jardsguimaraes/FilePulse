package br.com.jards.filepulse.batch.exception;

public class JobLaunchException extends RuntimeException {

    public JobLaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
