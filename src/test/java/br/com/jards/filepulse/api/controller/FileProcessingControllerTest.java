package br.com.jards.filepulse.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.jards.filepulse.api.dto.PaginaResponse;
import br.com.jards.filepulse.api.dto.ProcessamentoArquivoResponse;
import br.com.jards.filepulse.api.exception.FileProcessingNotFoundException;
import br.com.jards.filepulse.api.service.ProcessamentoArquivoService;
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;

@WebMvcTest(ProcessamentoArquivoController.class)
public class FileProcessingControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private ProcessamentoArquivoService fileProcessingService;

        @Test
        void deveRetornarNaoEncontradoQuandoProcessamentoNaoExistir() throws Exception {

                Long id = 999L;

                when(fileProcessingService.buscarPorId(id))
                                .thenThrow(new FileProcessingNotFoundException(id));

                mockMvc.perform(get("/api/v1/files/{id}", id))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.errorCode").value(404))
                                .andExpect(jsonPath("$.mensagemError")
                                                .value("Processamento de arquivo não encontrado para o ID: 999"));
        }

        @Test
        void deveRetornarProcessamentoQuandoIdExistir() throws Exception {

                Long id = 1L;

                var response = new ProcessamentoArquivoResponse(
                                id,
                                "clientes.csv",
                                StatusProcessamentoArquivo.COMPLETO,
                                40L,
                                40L,
                                0L,
                                LocalDateTime.of(2026, 10, 9, 10, 0),
                                LocalDateTime.of(2026, 10, 9, 10, 1),
                                null);

                when(fileProcessingService.buscarPorId(id))
                                .thenReturn(response);

                mockMvc.perform(get("/api/v1/files/{id}", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(1))
                                .andExpect(jsonPath("$.fileName").value("clientes.csv"))
                                .andExpect(jsonPath("$.status").value("COMPLETO"))
                                .andExpect(jsonPath("$.readCount").value(40))
                                .andExpect(jsonPath("$.writeCount").value(40))
                                .andExpect(jsonPath("$.skipCount").value(0))
                                .andExpect(jsonPath("$.fileHash").doesNotExist())
                                .andExpect(jsonPath("$.filePath").doesNotExist())
                                .andExpect(jsonPath("$.finalFilePath").doesNotExist());
        }

        @Test
        void deveRetornarPrimeiraPaginaComValoresPadrao() throws Exception {

                var response = new PaginaResponse<ProcessamentoArquivoResponse>(
                                List.of(),
                                0,
                                20,
                                0L,
                                0);

                when(fileProcessingService.listarTodos(0, 20))
                                .thenReturn(response);

                mockMvc.perform(get("/api/v1/files"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.content").isEmpty())
                                .andExpect(jsonPath("$.page").value(0))
                                .andExpect(jsonPath("$.size").value(20))
                                .andExpect(jsonPath("$.totalElements").value(0))
                                .andExpect(jsonPath("$.totalPages").value(0));

                verify(fileProcessingService).listarTodos(0, 20);
        }

        @Test
        void deveRetornarPaginaSolicitada() throws Exception {

                var response = new PaginaResponse<ProcessamentoArquivoResponse>(
                                List.of(),
                                2,
                                10,
                                35L,
                                4);

                when(fileProcessingService.listarTodos(2, 10))
                                .thenReturn(response);

                mockMvc.perform(get("/api/v1/files")
                                .param("page", "2")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.page").value(2))
                                .andExpect(jsonPath("$.size").value(10))
                                .andExpect(jsonPath("$.totalElements").value(35))
                                .andExpect(jsonPath("$.totalPages").value(4));

                verify(fileProcessingService).listarTodos(2, 10);
        }

        @Test
        void deveRetornarBadRequestQuandoPaginaForNegativa() throws Exception {

                when(fileProcessingService.listarTodos(-1, 20))
                                .thenThrow(new IllegalArgumentException(
                                                "O número da página não pode ser negativo"));

                mockMvc.perform(get("/api/v1/files")
                                .param("page", "-1"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value(400))
                                .andExpect(jsonPath("$.mensagemError")
                                                .value("O número da página não pode ser negativo"));

                verify(fileProcessingService).listarTodos(-1, 20);
        }

        @Test
        void deveRetornarBadRequestQuandoTamanhoForInvalido() throws Exception {

                when(fileProcessingService.listarTodos(0, 0))
                                .thenThrow(new IllegalArgumentException(
                                                "O tamanho da página deve estar entre 1 e 100"));

                mockMvc.perform(get("/api/v1/files")
                                .param("size", "0"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value(400))
                                .andExpect(jsonPath("$.mensagemError")
                                                .value("O tamanho da página deve estar entre 1 e 100"));

                verify(fileProcessingService).listarTodos(0, 0);
        }

        @Test
        void deveRetornarBadRequestQuandoPaginaNaoForNumerica() throws Exception {

                mockMvc.perform(get("/api/v1/files")
                                .param("page", "abc"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errorCode").value(400))
                                .andExpect(jsonPath("$.mensagemError")
                                                .value("Valor inválido para o parâmetro: page"));

                verifyNoInteractions(fileProcessingService);
        }
}