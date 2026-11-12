package br.com.jards.filepulse.batch.reader;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;

class LeitorMetadadosArquivoCsvTest {

        @TempDir
        Path diretorioTemporario;

        private LeitorMetadadosArquivoCsv leitor;

        private static final String CABECALHO = "id_registro|nome|cpf|agencia|conta|valor|mes_referencia";

        @BeforeEach
        void configurar() {
                leitor = new LeitorMetadadosArquivoCsv();
        }

        private Path criarArquivo(String conteudo) throws IOException {
                Path arquivo = diretorioTemporario.resolve("clientes.csv");

                Files.writeString(
                                arquivo,
                                conteudo,
                                StandardCharsets.UTF_8);

                return arquivo;
        }

        private String csvValido(String tipoArquivo) {
                return String.join("\n",
                                "idCarga|CAIXA-20261110-000001",
                                "tipoArquivo|" + tipoArquivo,
                                "totalRegistros|100000",
                                CABECALHO,
                                "REG-000001|João Silva|52998224725|1234|98765|1500.00|11/26",
                                "");
        }

        @Test
        void deveLerMetadadosDeArquivoOriginal() throws IOException {

                Path arquivo = criarArquivo(csvValido("ORIGINAL"));

                MetadadosArquivoCsv metadados = leitor.ler(arquivo);

                assertAll(
                                () -> assertEquals(
                                                "CAIXA-20261110-000001",
                                                metadados.idCarga()),

                                () -> assertEquals(
                                                TipoArquivoCsv.ORIGINAL,
                                                metadados.tipoArquivo()),

                                () -> assertEquals(
                                                100000L,
                                                metadados.totalRegistros()));
        }

        @Test
        void deveLerMetadadosDeArquivoComplementar() throws IOException {

                Path arquivo = criarArquivo(csvValido("COMPLEMENTAR"));

                MetadadosArquivoCsv metadados = leitor.ler(arquivo);

                assertEquals(
                                TipoArquivoCsv.COMPLEMENTAR,
                                metadados.tipoArquivo());
        }

        @Test
        void deveRejeitarIdentificadorDeCargaInvalido()
                        throws IOException {

                String conteudo = csvValido("ORIGINAL")
                                .replace(
                                                "CAIXA-20261110-000001",
                                                "CARGA-INVALIDA");

                Path arquivo = criarArquivo(conteudo);

                IllegalArgumentException erro = assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));

                erro.printStackTrace();
                assertTrue(erro.getMessage()
                                .contains("Identificador da carga inválido"));
        }

        @Test
        void deveRejeitarTipoDeArquivoInvalido()
                        throws IOException {

                Path arquivo = criarArquivo(csvValido("DESCONHECIDO"));

                IllegalArgumentException erro = assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));

                erro.printStackTrace();
                assertTrue(erro.getMessage()
                                .contains("Tipo de arquivo inválido"));
        }

        @Test
        void deveRejeitarTotalDeRegistrosZero()
                        throws IOException {

                String conteudo = csvValido("ORIGINAL")
                                .replace(
                                                "totalRegistros|100000",
                                                "totalRegistros|0");

                Path arquivo = criarArquivo(conteudo);

                IllegalArgumentException erro = assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));

                assertTrue(erro.getMessage()
                                .contains("deve ser maior que zero"));
        }

        @Test
        void deveRejeitarTotalDeRegistrosNaoNumerico()
                        throws IOException {

                String conteudo = csvValido("ORIGINAL")
                                .replace(
                                                "totalRegistros|100000",
                                                "totalRegistros|ABC");

                Path arquivo = criarArquivo(conteudo);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));
        }

        @Test
        void deveRejeitarCabecalhoInvalido()
                        throws IOException {

                String conteudo = csvValido("ORIGINAL")
                                .replace(
                                                CABECALHO,
                                                "nome|cpf|agencia|conta|valor|mes_referencia");

                Path arquivo = criarArquivo(conteudo);

                IllegalArgumentException erro = assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));

                assertEquals(
                                "Cabeçalho do CSV inválido",
                                erro.getMessage());
        }

        @Test
        void deveRejeitarMetadadoAusente()
                        throws IOException {

                Path arquivo = criarArquivo("""
                                idCarga|CAIXA-20261110-000001
                                tipoArquivo|ORIGINAL
                                """);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));
        }

        @Test
        void deveRejeitarMetadadosForaDeOrdem()
                        throws IOException {

                Path arquivo = criarArquivo("""
                                tipoArquivo|ORIGINAL
                                idCarga|CAIXA-20261110-000001
                                totalRegistros|100000
                                """ + CABECALHO);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));
        }

        @Test
        void deveRejeitarMetadadoVazio()
                        throws IOException {

                String conteudo = csvValido("ORIGINAL")
                                .replace(
                                                "idCarga|CAIXA-20261110-000001",
                                                "idCarga|");

                Path arquivo = criarArquivo(conteudo);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> leitor.ler(arquivo));
        }

        @Test
        void deveRejeitarArquivoInexistente() {

                Path arquivo = diretorioTemporario
                                .resolve("inexistente.csv");

                assertThrows(
                                IOException.class,
                                () -> leitor.ler(arquivo));
        }
}
