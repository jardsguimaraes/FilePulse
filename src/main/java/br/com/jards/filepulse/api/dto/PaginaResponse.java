package br.com.jards.filepulse.api.dto;

import java.util.List;

public record PaginaResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {
}
