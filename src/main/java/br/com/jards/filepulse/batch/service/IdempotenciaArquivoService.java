package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.model.ResultadoIdempotenciaArquivo;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class IdempotenciaArquivoService {

    private final HashArquivoService hashArquivoService;
    private final ProcessamentoArquivoRepository processamentoArquivoRepository;

    public IdempotenciaArquivoService(
            HashArquivoService hashArquivoService,
            ProcessamentoArquivoRepository processamentoArquivoRepository) {

        this.hashArquivoService = hashArquivoService;
        this.processamentoArquivoRepository = processamentoArquivoRepository;
    }

    public ResultadoIdempotenciaArquivo verificar(Path arquivo) {
        var hashArquivo = hashArquivoService.calcular(arquivo);
        var duplicado = processamentoArquivoRepository.existePorHash(hashArquivo);

        if (duplicado) {
            log.warn(
                    "Arquivo duplicado não será processado. Arquivo: {}, hash: {}",
                    arquivo, hashArquivo);
        }

        return new ResultadoIdempotenciaArquivo(hashArquivo, duplicado);
    }
}