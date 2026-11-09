package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.watcher.config.FileStabilityProperties;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class EstabilidadeArquivoService {

    private final ValidacaoArquivoService validacaoArquivoService;
    private final FileStabilityProperties propriedadesEstabilidade;

    public EstabilidadeArquivoService(ValidacaoArquivoService validacaoArquivoService,
            FileStabilityProperties propriedadesEstabilidade) {
        this.validacaoArquivoService = validacaoArquivoService;
        this.propriedadesEstabilidade = propriedadesEstabilidade;
    }

    public boolean aguardarEstabilizacao(Path arquivo) {
        for (int tentativa = 0; tentativa <= propriedadesEstabilidade.getMaxRetries(); tentativa++) {
            if (validacaoArquivoService.arquivoEstaEstavel(arquivo)) {
                return true;
            }

            if (tentativa < propriedadesEstabilidade.getMaxRetries()) {
                log.warn("Arquivo ainda não estabilizou. Nova tentativa será realizada. Arquivo: {}, tentativa: {}/{}",
                        arquivo, tentativa + 1, propriedadesEstabilidade.getMaxRetries());

                try {
                    Thread.sleep(propriedadesEstabilidade.getRetryIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }
}
