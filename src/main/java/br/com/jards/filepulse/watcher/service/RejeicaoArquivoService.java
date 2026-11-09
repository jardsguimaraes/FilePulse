package br.com.jards.filepulse.watcher.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.service.MovimentacaoArquivoService;
import br.com.jards.filepulse.watcher.enums.MotivoRejeicaoArquivo;
import br.com.jards.filepulse.watcher.repository.RejeicaoArquivoRepository;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class RejeicaoArquivoService {

    private final RejeicaoArquivoRepository rejeicaoArquivoRepository;
    private final MovimentacaoArquivoService movimentacaoArquivoService;

    public RejeicaoArquivoService(RejeicaoArquivoRepository rejeicaoArquivoRepository,
            MovimentacaoArquivoService movimentacaoArquivoService) {
        this.rejeicaoArquivoRepository = rejeicaoArquivoRepository;
        this.movimentacaoArquivoService = movimentacaoArquivoService;
    }

    public void rejeitar(Path arquivo, String hashArquivo, MotivoRejeicaoArquivo motivo, String detalhes) {
        var arquivoRejeitado = movimentacaoArquivoService.moverParaRejeitados(arquivo);

        auditar(arquivoRejeitado, hashArquivo, motivo, detalhes);
    }

    public void auditar(Path arquivo, String hashArquivo, MotivoRejeicaoArquivo motivo, String detalhes) {
        rejeicaoArquivoRepository.criar(
                arquivo.getFileName().toString(),
                arquivo.toString(),
                hashArquivo,
                motivo,
                detalhes);

        log.warn("Ocorrência de arquivo registrada. Arquivo: {}, motivo: {}, detalhes: {}", arquivo, motivo, detalhes);
    }
}
