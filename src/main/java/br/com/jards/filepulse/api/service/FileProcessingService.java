package br.com.jards.filepulse.api.service;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.api.dto.FileProcessingResponse;
import br.com.jards.filepulse.api.exception.FileProcessingNotFoundException;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;

@Service
public class FileProcessingService {

    private final FileProcessingRepository fileProcessingRepository;

    public FileProcessingService(FileProcessingRepository fileProcessingRepository) {
        this.fileProcessingRepository = fileProcessingRepository;
    }

    public FileProcessingResponse findById(Long id) {
        return fileProcessingRepository.findById(id).map(FileProcessingResponse::from)
                .orElseThrow(() -> new FileProcessingNotFoundException(id));
    }
}
