package br.com.jards.filepulse.api.dto;

import java.time.LocalDateTime;

import br.com.jards.filepulse.batch.enums.FileProcessingStatus;
import br.com.jards.filepulse.batch.model.FileProcessing;

public record FileProcessingResponse(
        Long id,
        String fileName,
        FileProcessingStatus status,
        Long readCount,
        Long writeCount,
        Long skipCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage) {

    public static FileProcessingResponse from(FileProcessing processing) {
        return new FileProcessingResponse(
                processing.id(),
                processing.fileName(),
                processing.status(),
                processing.readCount(),
                processing.writeCount(),
                processing.skipCount(),
                processing.startedAt(),
                processing.finishedAt(),
                processing.errorMessage());
    }
}
