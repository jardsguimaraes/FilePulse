package br.com.jards.filepulse.api.service;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.api.dto.FileProcessingResponse;
import br.com.jards.filepulse.api.dto.PageResponse;
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

    public PageResponse<FileProcessingResponse> findAll(int page, int size) {

        if (page < 0) {
            throw new IllegalArgumentException("O número da página não pode ser negativo");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("O tamanho da página deve estar entre 1 e 100");
        }

        var processamentos = fileProcessingRepository.findAll(page, size);

        var content = processamentos.stream()
                .map(FileProcessingResponse::from)
                .toList();

        long totalElements = fileProcessingRepository.countAll();

        int totalPages = (int) ((totalElements / size) + (totalElements % size == 0 ? 0 : 1));

        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
