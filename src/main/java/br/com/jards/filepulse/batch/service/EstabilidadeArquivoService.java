package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.watcher.config.EstabilidadeArquivoProperties;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class EstabilidadeArquivoService {

    private final ValidacaoArquivoService validacaoArquivoService;
    private final EstabilidadeArquivoProperties propriedadesEstabilidade;

    public EstabilidadeArquivoService(ValidacaoArquivoService validacaoArquivoService,
            EstabilidadeArquivoProperties propriedadesEstabilidade) {
        this.validacaoArquivoService = validacaoArquivoService;
        this.propriedadesEstabilidade = propriedadesEstabilidade;
    }

    public boolean aguardarEstabilizacao(Path arquivo) {
        for (int tentativa = 0; tentativa <= propriedadesEstabilidade.getMaximoRetentativas(); tentativa++) {
            if (validacaoArquivoService.arquivoEstaEstavel(arquivo)) {
                return true;
            }

            if (tentativa < propriedadesEstabilidade.getMaximoRetentativas()) {
                log.warn("Arquivo ainda não estabilizou. Nova tentativa será realizada. Arquivo: {}, tentativa: {}/{}",
                        arquivo, tentativa + 1, propriedadesEstabilidade.getMaximoRetentativas());

                try {
                    Thread.sleep(propriedadesEstabilidade.getIntervaloRetentativaMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false;
    }
}
