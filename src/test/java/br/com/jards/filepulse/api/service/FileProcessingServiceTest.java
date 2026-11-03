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

import br.com.jards.filepulse.api.dto.PaginaResponse;
import br.com.jards.filepulse.api.dto.ProcessamentoArquivoResponse;
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;

@ExtendWith(MockitoExtension.class)
public class FileProcessingServiceTest {

        @Mock
        private ProcessamentoArquivoRepository fileProcessingRepository;

        @InjectMocks
        private ProcessamentoArquivoService fileProcessingService;

        @Test
        void deveCalcularTotalDePaginasQuandoExistiremRegistros() {

                when(fileProcessingRepository.listarTodos(0, 10))
                                .thenReturn(List.of());

                when(fileProcessingRepository.contarTodos())
                                .thenReturn(35L);

                PaginaResponse<ProcessamentoArquivoResponse> response = fileProcessingService.listarTodos(0, 10);

                assertEquals(0, response.page());
                assertEquals(10, response.size());
                assertEquals(35L, response.totalElements());
                assertEquals(4, response.totalPages());

                verify(fileProcessingRepository).listarTodos(0, 10);
                verify(fileProcessingRepository).contarTodos();
        }

        @Test
        void deveRetornarZeroPaginasQuandoNaoExistiremRegistros() {

                when(fileProcessingRepository.listarTodos(0, 10))
                                .thenReturn(List.of());

                when(fileProcessingRepository.contarTodos())
                                .thenReturn(0L);

                PaginaResponse<ProcessamentoArquivoResponse> response = fileProcessingService.listarTodos(0, 10);

                assertTrue(response.content().isEmpty());
                assertEquals(0, response.page());
                assertEquals(10, response.size());
                assertEquals(0L, response.totalElements());
                assertEquals(0, response.totalPages());

                verify(fileProcessingRepository).listarTodos(0, 10);
                verify(fileProcessingRepository).contarTodos();
        }

        @Test
        void deveLancarExcecaoQuandoPaginaForNegativa() {

                int page = -1;
                int size = 10;

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> fileProcessingService.listarTodos(page, size));

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
                                () -> fileProcessingService.listarTodos(page, size));

                assertEquals(
                                "O tamanho da página deve estar entre 1 e 100",
                                exception.getMessage());

                verifyNoInteractions(fileProcessingRepository);
        }

        @Test
        void deveConverterProcessamentosEmRespostasPaginadas() {

                Long id = 1L;

                var processamento = new ProcessamentoArquivo(
                                id,
                                "clientes.csv",
                                "/entrada/clientes.csv",
                                "/processados/clientes.csv",
                                "hash-exemplo",
                                StatusProcessamentoArquivo.COMPLETO,
                                40L,
                                40L,
                                0L,
                                LocalDateTime.of(2026, 10, 29, 10, 0),
                                LocalDateTime.of(2026, 10, 29, 10, 1),
                                null);

                when(fileProcessingRepository.listarTodos(0, 10))
                                .thenReturn(List.of(processamento));

                when(fileProcessingRepository.contarTodos())
                                .thenReturn(1L);

                PaginaResponse<ProcessamentoArquivoResponse> response = fileProcessingService.listarTodos(0, 10);

                assertEquals(1, response.content().size());
                assertEquals(1L, response.totalElements());
                assertEquals(1, response.totalPages());

                ProcessamentoArquivoResponse arquivo = response.content().get(0);

                assertEquals(id, arquivo.id());
                assertEquals("clientes.csv", arquivo.fileName());
                assertEquals(StatusProcessamentoArquivo.COMPLETO, arquivo.status());
                assertEquals(40L, arquivo.readCount());
                assertEquals(40L, arquivo.writeCount());
                assertEquals(0L, arquivo.skipCount());

                verify(fileProcessingRepository).listarTodos(0, 10);
                verify(fileProcessingRepository).contarTodos();
        }
}
