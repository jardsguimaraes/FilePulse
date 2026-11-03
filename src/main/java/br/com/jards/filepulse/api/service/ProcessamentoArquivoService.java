package br.com.jards.filepulse.api.service;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.api.dto.PaginaResponse;
import br.com.jards.filepulse.api.dto.ProcessamentoArquivoResponse;
import br.com.jards.filepulse.api.exception.FileProcessingNotFoundException;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;

@Service
public class ProcessamentoArquivoService {

    private final ProcessamentoArquivoRepository ProcessamentoArquivoRepository;

    public ProcessamentoArquivoService(ProcessamentoArquivoRepository ProcessamentoArquivoRepository) {
        this.ProcessamentoArquivoRepository = ProcessamentoArquivoRepository;
    }

    public ProcessamentoArquivoResponse buscarPorId(Long id) {
        return ProcessamentoArquivoRepository.buscarPorId(id).map(ProcessamentoArquivoResponse::from)
                .orElseThrow(() -> new FileProcessingNotFoundException(id));
    }

    public PaginaResponse<ProcessamentoArquivoResponse> listarTodos(int page, int size) {

        if (page < 0) {
            throw new IllegalArgumentException("O número da página não pode ser negativo");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("O tamanho da página deve estar entre 1 e 100");
        }

        var processamentos = ProcessamentoArquivoRepository.listarTodos(page, size);

        var content = processamentos.stream()
                .map(ProcessamentoArquivoResponse::from)
                .toList();

        long totalElements = ProcessamentoArquivoRepository.contarTodos();

        int totalPages = (int) ((totalElements / size) + (totalElements % size == 0 ? 0 : 1));

        return new PaginaResponse<>(content, page, size, totalElements, totalPages);
    }
}
