package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.exception.MovimentacaoArquivoException;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class MovimentacaoArquivoService {

    private final ArmazenamentoArquivoProperties propriedadesArmazenamento;

    public MovimentacaoArquivoService(ArmazenamentoArquivoProperties propriedadesArmazenamento) {
        this.propriedadesArmazenamento = propriedadesArmazenamento;
    }

    public Path moverParaProcessamento(Path arquivo) {
        try {
            var arquivoMovido = mover(arquivo, Path.of(propriedadesArmazenamento.getDiretorioProcessamento()));
            log.info("Arquivo movido para processamento: {}", arquivoMovido);
            return arquivoMovido;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException(
                    "Não foi possível mover o arquivo para processamento: %s".formatted(arquivo),
                    e);
        }
    }

    public Path moverParaProcessados(Path arquivo) {
        try {
            var arquivoMovido = mover(arquivo, Path.of(propriedadesArmazenamento.getDiretorioProcessados()));

            log.info("Arquivo movido para processados: {}", arquivoMovido);
            return arquivoMovido;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException(
                    "Não foi possível mover o arquivo para processados: %s".formatted(arquivo), e);
        }
    }

    public Path moverParaErro(Path arquivo) {
        try {
            var arquivoMovido = mover(arquivo, Path.of(propriedadesArmazenamento.getDiretorioErro()));

            log.info("Arquivo movido para erro: {}", arquivoMovido);

            return arquivoMovido;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException("Não foi possível mover o arquivo para erro: %s".formatted(arquivo), e);
        }
    }

    public Path moverParaRejeitados(Path arquivo) {
        try {
            var arquivoMovido = mover(arquivo, Path.of(propriedadesArmazenamento.getDiretorioRejeitados()));

            log.info("Arquivo movido para rejeitados: {}", arquivoMovido);

            return arquivoMovido;
        } catch (IOException e) {
            throw new MovimentacaoArquivoException(
                    "Não foi possível mover o arquivo para rejeitados: %s".formatted(arquivo), e);
        }
    }

    private Path resolverDestino(Path diretorio, Path arquivo) {
        var destino = diretorio.resolve(arquivo.getFileName());

        if (!Files.exists(destino)) {
            return destino;
        }

        return criarDestinoUnico(diretorio, arquivo);
    }

    private Path mover(Path arquivo, Path diretorio) throws IOException {
        var destino = resolverDestino(diretorio, arquivo);

        try {
            return Files.move(arquivo, destino);
        } catch (FileAlreadyExistsException e) {
            var destinoUnico = criarDestinoUnico(diretorio, arquivo);
            return Files.move(arquivo, destinoUnico);
        }
    }

    private Path criarDestinoUnico(Path diretorio, Path arquivo) {
        var nomeArquivo = arquivo.getFileName().toString();
        var indicePonto = nomeArquivo.lastIndexOf('.');

        var nomeBase = indicePonto > 0
                ? nomeArquivo.substring(0, indicePonto)
                : nomeArquivo;

        var extensao = indicePonto > 0
                ? nomeArquivo.substring(indicePonto)
                : "";

        var nomeArquivoUnico = "%s_%s%s".formatted(nomeBase, UUID.randomUUID(), extensao);

        return diretorio.resolve(nomeArquivoUnico);
    }
}
