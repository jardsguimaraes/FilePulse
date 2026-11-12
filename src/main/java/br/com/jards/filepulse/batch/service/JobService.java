package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.exception.InicializacaoJobException;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;
import br.com.jards.filepulse.batch.reader.LeitorMetadadosArquivoCsv;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class JobService {

    private final JobLauncher jobLauncher;
    private final Job processamentoArquivoJob;
    private final ProcessamentoArquivoRepository processamentoArquivoRepository;
    private final MovimentacaoArquivoService movimentacaoArquivoService;
    private final LeitorMetadadosArquivoCsv leitorMetadadosArquivoCsv;
    private final CargaLogicaService cargaLogicaService;

    public JobService(JobLauncher jobLauncher, @Qualifier("processarArquivoJob") Job processamentoArquivoJob,
            ProcessamentoArquivoRepository processamentoArquivoRepository,
            MovimentacaoArquivoService movimentacaoArquivoService, CargaLogicaService cargaLogicaService,
            LeitorMetadadosArquivoCsv leitorMetadadosArquivoCsv) {
        this.jobLauncher = jobLauncher;
        this.processamentoArquivoJob = processamentoArquivoJob;
        this.processamentoArquivoRepository = processamentoArquivoRepository;
        this.movimentacaoArquivoService = movimentacaoArquivoService;
        this.leitorMetadadosArquivoCsv = leitorMetadadosArquivoCsv;
        this.cargaLogicaService = cargaLogicaService;
    }

    public boolean iniciar(Path arquivo, String hashArquivo) {

        if (processamentoArquivoRepository.existePorHash(hashArquivo)) {
            log.warn(
                    "Processamento não iniciado. Já existe um arquivo registrado com o hash: {}",
                    hashArquivo);
            return false;
        }

        MetadadosArquivoCsv metadados;

        try {
            metadados = leitorMetadadosArquivoCsv.ler(arquivo);
        } catch (IOException e) {
            throw new InicializacaoJobException(
                    "Não foi possível ler os metadados do arquivo %s".formatted(arquivo),
                    e);
        }

        var cargaLogica = cargaLogicaService.prepararCarga(metadados);

        Long idProcessamentoArquivo;

        try {
            idProcessamentoArquivo = processamentoArquivoRepository.criar(
                    arquivo.getFileName().toString(),
                    arquivo.toString(),
                    hashArquivo,
                    cargaLogica.id(),
                    metadados.tipoArquivo());

        } catch (DuplicateKeyException e) {
            log.warn(
                    "Processamento não iniciado. Já existe um arquivo registrado com o hash: {}",
                    hashArquivo);
            return false;
        }

        var parametros = construirParametrosJob(
                arquivo,
                idProcessamentoArquivo,
                cargaLogica.id());

        try {
            jobLauncher.run(processamentoArquivoJob, parametros);
            return true;

        } catch (JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException e) {

            log.error("Erro ao iniciar o Job para o arquivo {}", arquivo, e);

            throw new InicializacaoJobException(
                    "Não foi possível iniciar o Job para o arquivo %s".formatted(arquivo),
                    e);
        }
    }

    public void reiniciar(Long idProcessamentoArquivo) {
        var processamento = processamentoArquivoRepository.buscarPorId(idProcessamentoArquivo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Processamento não encontrado: %s".formatted(idProcessamentoArquivo)));

        if (processamento.status() != StatusProcessamentoArquivo.FALHOU) {
            throw new IllegalStateException(
                    "Apenas processamentos com status FALHOU podem ser reiniciados. Status atual: %s"
                            .formatted(processamento.status()));
        }

        var caminhoFinalArquivo = processamento.caminhoFinalArquivo();

        if (caminhoFinalArquivo == null || caminhoFinalArquivo.isBlank()) {
            throw new IllegalStateException(
                    "Processamento não possui arquivo disponível para restart: %s".formatted(idProcessamentoArquivo));
        }

        Path arquivoProcessamento = null;

        try {
            Long idCargaLogica = processamentoArquivoRepository.buscarIdCargaLogica(idProcessamentoArquivo);

            if (idCargaLogica == null) {
                throw new IllegalArgumentException("Processamento não possui carga lógica associada: %d"
                        .formatted(idProcessamentoArquivo));
            }
            arquivoProcessamento = movimentacaoArquivoService.moverParaProcessamento(Path.of(caminhoFinalArquivo));
            var parametros = construirParametrosJob(arquivoProcessamento, idProcessamentoArquivo, idCargaLogica);
            jobLauncher.run(processamentoArquivoJob, parametros);
        } catch (JobExecutionAlreadyRunningException | JobRestartException
                | JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {

            devolverArquivoParaErro(arquivoProcessamento, idProcessamentoArquivo);

            log.error("Erro ao reiniciar o processamento {}", idProcessamentoArquivo, e);
            throw new InicializacaoJobException(
                    "Não foi possível reiniciar o processamento %d".formatted(idProcessamentoArquivo),
                    e);
        }
    }

    private JobParameters construirParametrosJob(
            Path arquivo,
            Long idProcessamentoArquivo,
            Long idCargaLogica) {

        return new JobParametersBuilder()
                .addString("filePath", arquivo.toString(), false)
                .addLong("fileProcessingId", idProcessamentoArquivo)
                .addLong("logicalLoadId", idCargaLogica, false)
                .addLong("timestamp", System.currentTimeMillis(), false)
                .toJobParameters();
    }

    private void devolverArquivoParaErro(Path arquivoProcessamento, Long idProcessamentoArquivo) {
        if (arquivoProcessamento == null) {
            return;
        }

        try {
            var arquivoErro = movimentacaoArquivoService.moverParaErro(arquivoProcessamento);

            log.warn("Restart do processamento {} não foi iniciado. Arquivo devolvido para erro: {}",
                    idProcessamentoArquivo,
                    arquivoErro);
        } catch (RuntimeException excecaoReversao) {
            log.error("Falha ao devolver arquivo para erro após falha no restart do processamento {}. Arquivo: {}",
                    idProcessamentoArquivo,
                    arquivoProcessamento,
                    excecaoReversao);
        }
    }
}
