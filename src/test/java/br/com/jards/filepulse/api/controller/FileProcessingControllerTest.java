package br.com.jards.filepulse.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.jards.filepulse.api.dto.FileProcessingResponse;
import br.com.jards.filepulse.api.exception.FileProcessingNotFoundException;
import br.com.jards.filepulse.api.service.FileProcessingService;
import br.com.jards.filepulse.batch.enums.FileProcessingStatus;

@WebMvcTest(FileProcessingController.class)
public class FileProcessingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileProcessingService fileProcessingService;

    @Test
    void deveRetornarNaoEncontradoQuandoProcessamentoNaoExistir() throws Exception {

        Long id = 999L;

        when(fileProcessingService.findById(id))
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

        var response = new FileProcessingResponse(
                id,
                "clientes.csv",
                FileProcessingStatus.COMPLETED,
                40L,
                40L,
                0L,
                LocalDateTime.of(2026, 10, 9, 10, 0),
                LocalDateTime.of(2026, 10, 9, 10, 1),
                null);

        when(fileProcessingService.findById(id))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/files/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fileName").value("clientes.csv"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.readCount").value(40))
                .andExpect(jsonPath("$.writeCount").value(40))
                .andExpect(jsonPath("$.skipCount").value(0))
                .andExpect(jsonPath("$.fileHash").doesNotExist())
                .andExpect(jsonPath("$.filePath").doesNotExist())
                .andExpect(jsonPath("$.finalFilePath").doesNotExist());
    }
}