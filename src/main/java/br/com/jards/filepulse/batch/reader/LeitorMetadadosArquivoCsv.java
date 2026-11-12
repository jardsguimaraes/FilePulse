package br.com.jards.filepulse.batch.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;

@Component
public class LeitorMetadadosArquivoCsv {

    private static final Pattern PADRAO_ID_CARGA = Pattern.compile("^CAIXA-\\d{8}-\\d{6}$");
    private static final String CABECALHO_ESPERADO = "id_registro|nome|cpf|agencia|conta|valor|mes_referencia";

    public MetadadosArquivoCsv ler(Path caminhoArquivo) throws IOException {

        try (BufferedReader leitor = Files.newBufferedReader(caminhoArquivo, StandardCharsets.UTF_8)) {

            String idCarga = lerValor(leitor, "idCarga");
            String tipoArquivoTexto = lerValor(leitor, "tipoArquivo");
            String totalRegistrosTexto = lerValor(leitor, "totalRegistros");

            String cabecalho = leitor.readLine();

            if (!CABECALHO_ESPERADO.equals(cabecalho)) {
                throw new IllegalArgumentException("Cabeçalho do CSV inválido");
            }

            if (!PADRAO_ID_CARGA.matcher(idCarga).matches()) {
                throw new IllegalArgumentException("Identificador da carga inválido: %s".formatted(idCarga));
            }

            TipoArquivoCsv tipoArquivo;

            try {
                tipoArquivo = TipoArquivoCsv.valueOf(tipoArquivoTexto);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Tipo de arquivo inválido: %s".formatted(tipoArquivoTexto), e);
            }

            long totalRegistros;

            try {
                totalRegistros = Long.parseLong(totalRegistrosTexto);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Total de registros inválido: %s".formatted(totalRegistrosTexto), e);
            }

            if (totalRegistros <= 0) {
                throw new IllegalArgumentException("Total de registros deve ser maior que zero");
            }

            return new MetadadosArquivoCsv(
                    idCarga,
                    tipoArquivo,
                    totalRegistros);
        }
    }

    private String lerValor(BufferedReader leitor, String chaveEsperada) throws IOException {

        String linha = leitor.readLine();

        if (linha == null) {
            throw new IllegalArgumentException("Metadado ausente: %s".formatted(chaveEsperada));
        }

        String[] partes = linha.split("\\|", -1);

        if (partes.length != 2
                || !chaveEsperada.equals(partes[0])
                || partes[1].isBlank()) {

            throw new IllegalArgumentException("Metadado inválido: %s".formatted(chaveEsperada));
        }

        return partes[1];
    }
}