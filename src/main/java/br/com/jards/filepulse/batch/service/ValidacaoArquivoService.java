package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.watcher.config.FileStabilityProperties;

@Service
public class ValidacaoArquivoService {

    private final FileStabilityProperties propriedadesEstabilidade;
    private static final String CABECALHO_ESPERADO = "Nome|CPF|Agência|Conta|Valor|Mês de Referência";

    public ValidacaoArquivoService(FileStabilityProperties propriedadesEstabilidade) {
        this.propriedadesEstabilidade = propriedadesEstabilidade;
    }

    public boolean ehValido(Path arquivo) {
        if (!Files.isRegularFile(arquivo)) {
            return false;
        }

        return arquivo.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(".csv");
    }

    public boolean arquivoEstaEstavel(Path arquivo) {
        try {
            long tamanhoAnterior = -1;
            int verificacoesEstaveis = 0;
            int tentativas = 0;

            while (verificacoesEstaveis < propriedadesEstabilidade.getStableChecks()
                    && tentativas < propriedadesEstabilidade.getMaxAttempts()) {
                long tamanhoAtual = Files.size(arquivo);

                if (tamanhoAtual == tamanhoAnterior) {
                    verificacoesEstaveis++;
                } else {
                    verificacoesEstaveis = 0;
                }

                tamanhoAnterior = tamanhoAtual;
                tentativas++;
                Thread.sleep(propriedadesEstabilidade.getCheckIntervalMs());
            }

            return verificacoesEstaveis >= propriedadesEstabilidade.getStableChecks();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean possuiCabecalhoValido(Path arquivo) {
        try (var leitor = Files.newBufferedReader(arquivo)) {
            var primeiraLinha = leitor.readLine();
            return CABECALHO_ESPERADO.equals(primeiraLinha);
        } catch (IOException e) {
            return false;
        }
    }
}
