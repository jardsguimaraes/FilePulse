package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.repository.FileProcessingRepository;
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

    public Optional<String> getHashIfNotProcessed(Path file) {
        var fileHash = fileHashService.calculate(file);

        if (fileProcessingRepository.existByHash(fileHash)) {
            log.warn("Arquivo duplicado não será processado. Arquivo: {}, hash: {}", file, fileHash);
            return Optional.empty();
        }

        return Optional.of(fileHash);
    }
}