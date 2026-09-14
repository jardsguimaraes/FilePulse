package br.com.jards.filepulse.batch.model;

import java.time.LocalDateTime;

import br.com.jards.filepulse.batch.enums.FileProcessingStatus;

public record FileProcessing(
        Long id,
        String fileName,
        String filePath,
        String fileHash,
        FileProcessingStatus status,
        Long readCount,
        Long writeCount,
        Long skipCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage) {

}
