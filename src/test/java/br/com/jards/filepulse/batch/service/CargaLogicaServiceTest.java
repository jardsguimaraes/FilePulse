package br.com.jards.filepulse.batch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.model.CargaLogica;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;
import br.com.jards.filepulse.batch.repository.CargaLogicaRepository;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;

class CargaLogicaServiceTest {

        private static final String ID_CARGA = "CAIXA-20261110-000001";

        private static final long TOTAL_REGISTROS = 100000L;

        private CargaLogicaRepository repository;
        private ProcessamentoArquivoRepository processamentoArquivoRepository;
        private ArmazenamentoArquivoProperties armazenamentoArquivoProperties;
        private CargaLogicaService service;

        @BeforeEach
        void configurar() {
                repository = mock(CargaLogicaRepository.class);

                armazenamentoArquivoProperties = mock(ArmazenamentoArquivoProperties.class);

                processamentoArquivoRepository = mock(ProcessamentoArquivoRepository.class);

                service = new CargaLogicaService(
                                repository,
                                armazenamentoArquivoProperties,
                                processamentoArquivoRepository);
        }

        @Test
        void deveCriarCargaOriginalQuandoNaoExiste() {

                var metadados = metadados(TipoArquivoCsv.ORIGINAL);
                var cargaCriada = carga(StatusCargaLogica.INCOMPLETA);

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.empty());

                when(repository.criar(ID_CARGA, TOTAL_REGISTROS))
                                .thenReturn(cargaCriada);

                CargaLogica resultado = service.prepararCarga(metadados);

                assertEquals(cargaCriada, resultado);

                verify(repository).criar(ID_CARGA, TOTAL_REGISTROS);
        }

        @Test
        void deveRejeitarCargaOriginalDuplicada() {

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(
                                                carga(StatusCargaLogica.INCOMPLETA)));

                assertThrows(IllegalArgumentException.class,
                                () -> service.prepararCarga(metadados(TipoArquivoCsv.ORIGINAL)));

                verify(repository, never()).criar(anyString(), anyLong());
        }

        @Test
        void deveRejeitarComplementarSemCargaOriginal() {

                when(repository.buscarPorIdCarga(ID_CARGA)).thenReturn(Optional.empty());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> service.prepararCarga(metadados(TipoArquivoCsv.COMPLEMENTAR)));

                verify(repository, never()).criar(anyString(), anyLong());
        }

        @Test
        void deveRejeitarComplementarComCargaConcluida() {

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(carga(StatusCargaLogica.CONCLUIDA)));

                IllegalArgumentException erro = assertThrows(
                                IllegalArgumentException.class,
                                () -> service.prepararCarga(metadados(TipoArquivoCsv.COMPLEMENTAR)));

                assertTrue(erro.getMessage().contains("não está disponível para complementação"));
        }

        @Test
        void deveRejeitarComplementarComTotalDivergente() {

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(carga(StatusCargaLogica.INCOMPLETA)));

                var metadados = new MetadadosArquivoCsv(
                                ID_CARGA,
                                TipoArquivoCsv.COMPLEMENTAR,
                                90000L);

                IllegalArgumentException erro = assertThrows(
                                IllegalArgumentException.class,
                                () -> service.prepararCarga(metadados));

                assertTrue(erro.getMessage().contains("Total de registros divergente"));
        }

        @Test
        void deveReutilizarCargaIncompletaParaComplementar() {

                var cargaExistente = carga(StatusCargaLogica.INCOMPLETA);

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(cargaExistente));

                CargaLogica resultado = service.prepararCarga(metadados(TipoArquivoCsv.COMPLEMENTAR));

                assertEquals(cargaExistente, resultado);

                verify(repository, never()).criar(anyString(), anyLong());
        }

        @Test
        void deveRejeitarMetadadosNulos() {

                NullPointerException erro = assertThrows(
                                NullPointerException.class,
                                () -> service.prepararCarga(null));

                assertEquals("Os metadados do arquivo são obrigatórios", erro.getMessage());

                verifyNoInteractions(repository);
        }

        @Test
        void deveBloquearCargaAntesDeReservarComplementar() {

                var metadados = metadados(TipoArquivoCsv.COMPLEMENTAR);
                var cargaExistente = carga(StatusCargaLogica.INCOMPLETA);
                var arquivo = Path.of("complementar.csv");
                var hashArquivo = "hash-teste";

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(cargaExistente));

                when(processamentoArquivoRepository.criar(
                                anyString(),
                                anyString(),
                                anyString(),
                                anyLong(),
                                any(TipoArquivoCsv.class)))
                                .thenReturn(20L);

                var resultado = service.prepararCargaEReservarProcessamento(
                                metadados,
                                arquivo,
                                hashArquivo);

                var ordem = inOrder(repository, processamentoArquivoRepository);

                ordem.verify(repository).bloquearCarga(cargaExistente.id());

                ordem.verify(processamentoArquivoRepository).criar(
                                arquivo.getFileName().toString(),
                                arquivo.toString(),
                                hashArquivo,
                                cargaExistente.id(),
                                TipoArquivoCsv.COMPLEMENTAR);

                assertEquals(20L, resultado.idProcessamentoArquivo());
                assertEquals(cargaExistente.id(), resultado.idCargaLogica());
        }

        private MetadadosArquivoCsv metadados(
                        TipoArquivoCsv tipoArquivo) {

                return new MetadadosArquivoCsv(
                                ID_CARGA,
                                tipoArquivo,
                                TOTAL_REGISTROS);
        }

        private CargaLogica carga(StatusCargaLogica status) {

                return new CargaLogica(
                                10L,
                                ID_CARGA,
                                TOTAL_REGISTROS,
                                status);
        }

        @Test
        void deveRejeitarOriginalQuandoCargaJaConcluida() {
                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(
                                                carga(StatusCargaLogica.CONCLUIDA)));

                assertThrows(IllegalArgumentException.class,
                                () -> service.prepararCarga(
                                                metadados(TipoArquivoCsv.ORIGINAL)));

                verify(repository, never()).criar(anyString(), anyLong());
        }

        @Test
        void deveCriarCargaOriginalEReservarProcessamento() {
                var arquivo = Path.of("original.csv");
                var cargaCriada = carga(StatusCargaLogica.INCOMPLETA);

                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.empty());

                when(repository.criar(ID_CARGA, TOTAL_REGISTROS))
                                .thenReturn(cargaCriada);

                when(processamentoArquivoRepository.criar(
                                anyString(), anyString(), anyString(),
                                anyLong(), any(TipoArquivoCsv.class)))
                                .thenReturn(20L);

                var resultado = service.prepararCargaEReservarProcessamento(
                                metadados(TipoArquivoCsv.ORIGINAL),
                                arquivo,
                                "hash-original");

                var ordem = inOrder(repository, processamentoArquivoRepository);

                ordem.verify(repository).criar(ID_CARGA, TOTAL_REGISTROS);

                ordem.verify(processamentoArquivoRepository).criar(
                                "original.csv",
                                arquivo.toString(),
                                "hash-original",
                                10L,
                                TipoArquivoCsv.ORIGINAL);

                assertEquals(20L, resultado.idProcessamentoArquivo());
                assertEquals(10L, resultado.idCargaLogica());
        }

        @Test
        void deveRejeitarReservaComplementarSemCargaExistente() {
                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.empty());

                assertThrows(IllegalArgumentException.class,
                                () -> service.prepararCargaEReservarProcessamento(
                                                metadados(TipoArquivoCsv.COMPLEMENTAR),
                                                Path.of("complementar.csv"),
                                                "hash"));

                verifyNoInteractions(processamentoArquivoRepository);
                verify(repository, never()).bloquearCarga(anyLong());
        }

        @Test
        void deveRejeitarReservaDeCargaConcluida() {
                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(
                                                carga(StatusCargaLogica.CONCLUIDA)));

                assertThrows(IllegalArgumentException.class,
                                () -> service.prepararCargaEReservarProcessamento(
                                                metadados(TipoArquivoCsv.COMPLEMENTAR),
                                                Path.of("complementar.csv"),
                                                "hash"));

                verifyNoInteractions(processamentoArquivoRepository);
        }

        @Test
        void deveRejeitarReservaComTotalDivergente() {
                when(repository.buscarPorIdCarga(ID_CARGA))
                                .thenReturn(Optional.of(
                                                carga(StatusCargaLogica.INCOMPLETA)));

                var metadados = new MetadadosArquivoCsv(
                                ID_CARGA,
                                TipoArquivoCsv.COMPLEMENTAR,
                                90000L);

                assertThrows(IllegalArgumentException.class,
                                () -> service.prepararCargaEReservarProcessamento(
                                                metadados,
                                                Path.of("complementar.csv"),
                                                "hash"));

                verifyNoInteractions(processamentoArquivoRepository);
        }

        @Test
        void deveConcluirCargaQuandoRepositoryConfirmarConclusao() {
                when(repository.concluirSeCompleta(10L))
                                .thenReturn(true);

                boolean resultado = service.conciliarCarga(10L);

                assertTrue(resultado);

                var ordem = inOrder(repository);
                ordem.verify(repository).bloquearCarga(10L);
                ordem.verify(repository).concluirSeCompleta(10L);
        }

        @Test
        void deveManterCargaIncompletaQuandoNaoAtenderCriterios() {
                when(repository.concluirSeCompleta(10L))
                                .thenReturn(false);

                boolean resultado = service.conciliarCarga(10L);

                assertFalse(resultado);
                verify(repository).bloquearCarga(10L);
                verify(repository).concluirSeCompleta(10L);
        }

        @Test
        void deveRejeitarConciliacaoSemId() {
                assertThrows(NullPointerException.class,
                                () -> service.conciliarCarga(null));

                verifyNoInteractions(repository);
        }

        @Test
        void deveRejeitarReservaComMetadadosNulos() {
                assertThrows(NullPointerException.class,
                                () -> service.prepararCargaEReservarProcessamento(
                                                null,
                                                Path.of("arquivo.csv"),
                                                "hash"));

                verifyNoInteractions(repository, processamentoArquivoRepository);
        }

        @Test
        void deveRejeitarReservaComArquivoNulo() {
                assertThrows(NullPointerException.class,
                                () -> service.prepararCargaEReservarProcessamento(
                                                metadados(TipoArquivoCsv.ORIGINAL),
                                                null,
                                                "hash"));

                verifyNoInteractions(repository, processamentoArquivoRepository);
        }

        @Test
        void deveRejeitarReservaComHashNulo() {
                assertThrows(NullPointerException.class,
                                () -> service.prepararCargaEReservarProcessamento(
                                                metadados(TipoArquivoCsv.ORIGINAL),
                                                Path.of("arquivo.csv"),
                                                null));

                verifyNoInteractions(repository, processamentoArquivoRepository);
        }
}
