package br.com.jards.filepulse.batch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.model.CargaLogica;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;
import br.com.jards.filepulse.batch.repository.CargaLogicaRepository;

class CargaLogicaServiceTest {

    private static final String ID_CARGA = "CAIXA-20261110-000001";

    private static final long TOTAL_REGISTROS = 100000L;

    private CargaLogicaRepository repository;
    private CargaLogicaService service;

    @BeforeEach
    void configurar() {
        repository = mock(CargaLogicaRepository.class);
        service = new CargaLogicaService(repository);
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
}
