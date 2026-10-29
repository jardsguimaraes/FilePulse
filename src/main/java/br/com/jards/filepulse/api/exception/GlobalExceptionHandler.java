package br.com.jards.filepulse.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.log4j.Log4j2;

@Log4j2
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FileProcessingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlerNotFoundException(FileProcessingNotFoundException ex) {
        var errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
        log.warn("Processamento não encontrado: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handlerIllegalArgumentException(IllegalArgumentException ex) {
        var errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);

        log.warn("Parâmetro inválido: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        var errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Valor inválido para o parâmetro: " + ex.getName(),
                null);

        log.warn("Parâmetro HTTP inválido: {}", ex.getName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
}
