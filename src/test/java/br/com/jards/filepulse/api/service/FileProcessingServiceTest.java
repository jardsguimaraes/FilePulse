package br.com.jards.filepulse.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.jards.filepulse.api.dto.FileProcessingResponse;
import br.com.jards.filepulse.api.dto.PageResponse;
import br.com.jards.filepulse.batch.enums.FileProcessingStatus;
import br.com.jards.filepulse.batch.model.FileProcessing;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;

@ExtendWith(MockitoExtension.class)
public class FileProcessingServiceTest {

    @Mock
    private FileProcessingRepository fileProcessingRepository;

    @InjectMocks
    private FileProcessingService fileProcessingService;

    @Test
    void deveCalcularTotalDePaginasQuandoExistiremRegistros() {

        when(fileProcessingRepository.findAll(0, 10))
                .thenReturn(List.of());

        when(fileProcessingRepository.countAll())
                .thenReturn(35L);

        PageResponse<FileProcessingResponse> response = fileProcessingService.findAll(0, 10);

        assertEquals(0, response.page());
        assertEquals(10, response.size());
        assertEquals(35L, response.totalElements());
        assertEquals(4, response.totalPages());

        verify(fileProcessingRepository).findAll(0, 10);
        verify(fileProcessingRepository).countAll();
    }

    @Test
    void deveRetornarZeroPaginasQuandoNaoExistiremRegistros() {

        when(fileProcessingRepository.findAll(0, 10))
                .thenReturn(List.of());

        when(fileProcessingRepository.countAll())
                .thenReturn(0L);

        PageResponse<FileProcessingResponse> response = fileProcessingService.findAll(0, 10);

        assertTrue(response.content().isEmpty());
        assertEquals(0, response.page());
        assertEquals(10, response.size());
        assertEquals(0L, response.totalElements());
        assertEquals(0, response.totalPages());

        verify(fileProcessingRepository).findAll(0, 10);
        verify(fileProcessingRepository).countAll();
    }

    @Test
    void deveLancarExcecaoQuandoPaginaForNegativa() {

        int page = -1;
        int size = 10;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fileProcessingService.findAll(page, size));

        assertEquals(
                "O número da página não pode ser negativo",
                exception.getMessage());

        verifyNoInteractions(fileProcessingRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 101 })
    void deveLancarExcecaoQuandoTamanhoDaPaginaForInvalido(int size) {

        int page = 0;

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> fileProcessingService.findAll(page, size));

        assertEquals(
                "O tamanho da página deve estar entre 1 e 100",
                exception.getMessage());

        verifyNoInteractions(fileProcessingRepository);
    }

    @Test
    void deveConverterProcessamentosEmRespostasPaginadas() {

        Long id = 1L;

        var processamento = new FileProcessing(
                id,
                "clientes.csv",
                "/entrada/clientes.csv",
                "/processados/clientes.csv",
                "hash-exemplo",
                FileProcessingStatus.COMPLETED,
                40L,
                40L,
                0L,
                LocalDateTime.of(2026, 10, 29, 10, 0),
                LocalDateTime.of(2026, 10, 29, 10, 1),
                null);

        when(fileProcessingRepository.findAll(0, 10))
                .thenReturn(List.of(processamento));

        when(fileProcessingRepository.countAll())
                .thenReturn(1L);

        PageResponse<FileProcessingResponse> response = fileProcessingService.findAll(0, 10);

        assertEquals(1, response.content().size());
        assertEquals(1L, response.totalElements());
        assertEquals(1, response.totalPages());

        FileProcessingResponse arquivo = response.content().get(0);

        assertEquals(id, arquivo.id());
        assertEquals("clientes.csv", arquivo.fileName());
        assertEquals(FileProcessingStatus.COMPLETED, arquivo.status());
        assertEquals(40L, arquivo.readCount());
        assertEquals(40L, arquivo.writeCount());
        assertEquals(0L, arquivo.skipCount());

        verify(fileProcessingRepository).findAll(0, 10);
        verify(fileProcessingRepository).countAll();
    }
}
