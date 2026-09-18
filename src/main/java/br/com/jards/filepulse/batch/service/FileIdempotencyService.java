package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.repository.FileProcessingRepository;
import br.com.jards.filepulse.watcher.model.FileIdempotencyResult;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileIdempotencyService {

    private final FileHashService fileHashService;
    private final FileProcessingRepository fileProcessingRepository;

    public FileIdempotencyService(FileHashService fileHashService, FileProcessingRepository fileProcessingRepository) {
        this.fileHashService = fileHashService;
        this.fileProcessingRepository = fileProcessingRepository;
    }

    public FileIdempotencyResult check(Path file) {
        var fileHash = fileHashService.calculate(file);
        var duplicate = fileProcessingRepository.existByHash(fileHash);

        if (duplicate) {
            log.warn("Arquivo duplicado não será processado. Arquivo: {}, hash: {}", file, fileHash);
        }

        return new FileIdempotencyResult(fileHash, duplicate);
    }
}