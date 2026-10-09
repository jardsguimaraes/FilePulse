package br.com.jards.filepulse.api.exception;

public class FileProcessingNotFoundException extends RuntimeException {

    public FileProcessingNotFoundException(Long id) {
        super("Processamento de arquivo não encontrado para o ID: %d".formatted(id));
    }

}
