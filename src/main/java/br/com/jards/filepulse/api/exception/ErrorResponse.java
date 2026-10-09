package br.com.jards.filepulse.api.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int errorCode,
        String mensagemError,
        Map<String, String> errorDetails) {

}
