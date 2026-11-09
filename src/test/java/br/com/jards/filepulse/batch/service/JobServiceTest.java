package br.com.jards.filepulse.batch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRestartException;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.exception.InicializacaoJobException;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;

@ExtendWith(MockitoExtension.class)
public class JobServiceTest {

        @Mock
        private JobLauncher jobLauncher;

        @Mock
        private Job fileProcessJob;

        @Mock
        private ProcessamentoArquivoRepository fileProcessingRepository;

        @Mock
        private MovimentacaoArquivoService fileMovementService;

        @InjectMocks
        private JobService jobService;

        @Test
        void shouldNotRestartCompletedProcessing() {

                var processing = new ProcessamentoArquivo(
                                42L,
                                "clientes.csv",
                                "./files/processamento/clientes.csv",
                                "./files/processados/clientes.csv",
                                "hash-teste",
                                StatusProcessamentoArquivo.COMPLETO,
                                40L,
                                40L,
                                0L,
                                LocalDateTime.now(),
                                LocalDateTime.now(),
                                null);

                when(fileProcessingRepository.buscarPorId(42L))
                                .thenReturn(Optional.of(processing));

                assertThrows(
                                IllegalStateException.class,
                                () -> jobService.reiniciar(42L));
        }

        @Test
        void shouldRestartFailedProcessing() throws Exception {

                var processing = new ProcessamentoArquivo(
                                42L,
                                "clientes.csv",
                                "./files/processamento/clientes.csv",
                                "./files/erro/clientes.csv",
                                "hash-teste",
                                StatusProcessamentoArquivo.FALHOU,
                                30L,
                                30L,
                                0L,
                                LocalDateTime.now(),
                                LocalDateTime.now(),
                                "Falha controlada");

                var errorFile = Path.of("./files/erro/clientes.csv");
                var processingFile = Path.of("./files/processamento/clientes.csv");

                when(fileProcessingRepository.buscarPorId(42L))
                                .thenReturn(Optional.of(processing));

                when(fileMovementService.moverParaProcessamento(errorFile))
                                .thenReturn(processingFile);

                jobService.reiniciar(42L);

                verify(fileMovementService)
                                .moverParaProcessamento(errorFile);

                var parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

                verify(jobLauncher).run(eq(fileProcessJob),
                                parametersCaptor.capture());

                var parameters = parametersCaptor.getValue();

                assertEquals(42L, parameters.getLong("fileProcessingId"));
                assertEquals(processingFile.toString(), parameters.getString("filePath"));

                var identifyingParameters = parameters.getIdentifyingParameters();

                assertTrue(identifyingParameters.containsKey("fileProcessingId"));
                assertFalse(identifyingParameters.containsKey("filePath"));
                assertFalse(identifyingParameters.containsKey("timestamp"));
        }

        @Test
        void shouldMoveFileBackToErrorWhenRestartCannotBeStarted() throws Exception {

                var processing = new ProcessamentoArquivo(
                                42L,
                                "clientes.csv",
                                "./files/processamento/clientes.csv",
                                "./files/erro/clientes.csv",
                                "hash-teste",
                                StatusProcessamentoArquivo.FALHOU,
                                30L,
                                30L,
                                0L,
                                LocalDateTime.now(),
                                LocalDateTime.now(),
                                "Falha controlada");

                var errorFile = Path.of("./files/erro/clientes.csv");
                var processingFile = Path.of("./files/processamento/clientes.csv");
                var returnedErrorFile = Path.of("./files/erro/clientes.csv");

                when(fileProcessingRepository.buscarPorId(42L))
                                .thenReturn(Optional.of(processing));

                when(fileMovementService.moverParaProcessamento(errorFile))
                                .thenReturn(processingFile);

                when(jobLauncher.run(
                                eq(fileProcessJob),
                                any(JobParameters.class)))
                                .thenThrow(new JobRestartException(
                                                "Restart recusado para teste"));

                when(fileMovementService.moverParaErro(processingFile))
                                .thenReturn(returnedErrorFile);

                assertThrows(
                                InicializacaoJobException.class,
                                () -> jobService.reiniciar(42L));

                verify(fileMovementService)
                                .moverParaProcessamento(errorFile);

                verify(fileMovementService)
                                .moverParaErro(processingFile);
        }
}
