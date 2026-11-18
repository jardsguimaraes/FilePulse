package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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
import br.com.jards.filepulse.batch.model.ReservaProcessamento;
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
    private final ControleExecucaoJobService controleExecucaoJobService;
    private final IdentidadeInstanciaService identidadeInstanciaService;
    private final MonitoramentoInstanciaService monitoramentoInstanciaService;

    public JobService(JobLauncher jobLauncher, @Qualifier("processarArquivoJob") Job processamentoArquivoJob,
            ProcessamentoArquivoRepository processamentoArquivoRepository,
            MovimentacaoArquivoService movimentacaoArquivoService, CargaLogicaService cargaLogicaService,
            LeitorMetadadosArquivoCsv leitorMetadadosArquivoCsv,
            ControleExecucaoJobService controleExecucaoJobService,
            IdentidadeInstanciaService identidadeInstanciaService,
            MonitoramentoInstanciaService monitoramentoInstanciaService) {
        this.jobLauncher = jobLauncher;
        this.processamentoArquivoJob = processamentoArquivoJob;
        this.processamentoArquivoRepository = processamentoArquivoRepository;
        this.movimentacaoArquivoService = movimentacaoArquivoService;
        this.leitorMetadadosArquivoCsv = leitorMetadadosArquivoCsv;
        this.cargaLogicaService = cargaLogicaService;
        this.controleExecucaoJobService = controleExecucaoJobService;
        this.identidadeInstanciaService = identidadeInstanciaService;
        this.monitoramentoInstanciaService = monitoramentoInstanciaService;
    }

    public boolean iniciar(Path arquivo, String hashArquivo) {

        validarAutorizacaoInstancia();

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

        ReservaProcessamento reserva;

        try {
            reserva = cargaLogicaService.prepararCargaEReservarProcessamento(metadados, arquivo, hashArquivo);
        } catch (DuplicateKeyException e) {
            log.warn("Processamento não iniciado. Hash já registrado: {}", hashArquivo);

            return false;
        }

        var parametros = construirParametrosJob(
                arquivo,
                reserva.idProcessamentoArquivo(),
                reserva.idCargaLogica(),
                reserva.idTentativa());

        try {
            jobLauncher.run(processamentoArquivoJob, parametros);
            return true;

        } catch (JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException e) {

            log.error("Erro ao iniciar o Job para o arquivo {}", arquivo, e);

            throw new InicializacaoJobException(
                    "Não foi possível iniciar o Job para o arquivo %s".formatted(arquivo), e);
        }
    }

    public void reiniciar(Long idProcessamentoArquivo) {

        validarAutorizacaoInstancia();

        var processamento = processamentoArquivoRepository
                .buscarPorId(idProcessamentoArquivo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Processamento não encontrado: %d".formatted(idProcessamentoArquivo)));

        if (processamento.status() != StatusProcessamentoArquivo.FALHOU) {
            throw new IllegalArgumentException("Apenas processamentos com status FALHOU podem ser reiniciados.");
        }

        String caminhoOriginal = processamento.caminhoFinalArquivo();

        if (caminhoOriginal == null || caminhoOriginal.isBlank()) {
            throw new IllegalArgumentException("Processamento sem caminho final registrado.");
        }

        Path arquivoOriginal = Path.of(caminhoOriginal);

        if (!Files.isRegularFile(arquivoOriginal)) {
            throw new IllegalArgumentException("Arquivo não encontrado: %s".formatted(arquivoOriginal));
        }

        Long idCargaLogica = processamentoArquivoRepository.buscarIdCargaLogica(idProcessamentoArquivo);

        if (idCargaLogica == null) {
            throw new IllegalArgumentException("Processamento sem carga lógica associada.");
        }

        UUID idTentativa = UUID.randomUUID();

        boolean preparacaoRegistrada = controleExecucaoJobService.iniciarPreparacao(idTentativa);

        if (!preparacaoRegistrada) {
            throw new IllegalStateException(
                    "Não foi possível registrar a preparação da tentativa %d".formatted(idTentativa));
        }

        boolean reservado;

        try {
            UUID idInstancia = identidadeInstanciaService.obterIdInstancia();
            reservado = processamentoArquivoRepository.reservarTentativa(
                    idProcessamentoArquivo,
                    idTentativa,
                    idInstancia,
                    arquivoOriginal.toString());

        } catch (RuntimeException e) {

            controleExecucaoJobService.finalizar(idTentativa);

            log.error(
                    "Falha ao reservar tentativa {} do processamento {}",
                    idTentativa,
                    idProcessamentoArquivo,
                    e);

            throw new InicializacaoJobException(
                    "Não foi possível reservar a tentativa do processamento "
                            + idProcessamentoArquivo,
                    e);
        }

        if (!reservado) {

            controleExecucaoJobService.finalizar(idTentativa);

            throw new IllegalStateException(
                    "Não foi possível reservar uma nova tentativa para o processamento %d"
                            .formatted(idProcessamentoArquivo));
        }

        Path arquivoProcessamento = null;

        try {
            arquivoProcessamento = movimentacaoArquivoService.moverParaProcessados(arquivoOriginal);

            boolean caminhoAtualizado = processamentoArquivoRepository.atualizarCaminhoTentativaAtiva(
                    idProcessamentoArquivo,
                    idTentativa,
                    caminhoOriginal.toString());

            if (!caminhoAtualizado) {
                throw new IllegalArgumentException("Não foi possível atualizar o caminho da tentativa ativa.");
            }
        } catch (RuntimeException erroPreparacao) {
            try {
                compensarFalhaPreparacao(
                        idProcessamentoArquivo,
                        idTentativa,
                        arquivoOriginal,
                        arquivoProcessamento,
                        erroPreparacao);
            } finally {
                controleExecucaoJobService.finalizar(idTentativa);
            }

            throw new InicializacaoJobException(
                    "Falha ao preparar o reinício do processamento %d".formatted(idProcessamentoArquivo),
                    erroPreparacao);
        }

        var parametros = construirParametrosJob(arquivoProcessamento, idProcessamentoArquivo, idCargaLogica,
                idTentativa);

        try {
            jobLauncher.run(processamentoArquivoJob, parametros);
        } catch (JobExecutionAlreadyRunningException
                | JobRestartException
                | JobInstanceAlreadyCompleteException
                | JobParametersInvalidException e) {

            try {
                compensarFalhaPreparacao(
                        idProcessamentoArquivo,
                        idTentativa,
                        arquivoOriginal,
                        arquivoProcessamento,
                        e);
            } finally {
                controleExecucaoJobService.finalizar(idTentativa);
            }

            throw new InicializacaoJobException(
                    "Não foi possível iniciar o Job do processamento %d".formatted(idProcessamentoArquivo), e);
        } catch (RuntimeException e) {
            log.error("Falha inesperada ao iniciar ou executar o Job. Processamento: {}, tentativa: {}. "
                    + "O arquivo não será movimentado automaticamente porque o estado da execução é incerto.",
                    idProcessamentoArquivo,
                    idTentativa,
                    e);

            throw new InicializacaoJobException(
                    "Não foi possível confirmar o estado do Job para o processamento %d"
                            .formatted(idProcessamentoArquivo),
                    e);
        }
    }

    private void compensarFalhaPreparacao(Long idProcessamentoArquivo, UUID idTentativa, Path arquivoOriginal,
            Path arquivoProcessamento, Exception causa) {

        Path caminhoErro = arquivoOriginal;

        if (arquivoProcessamento != null) {
            try {
                caminhoErro = movimentacaoArquivoService.moverParaErro(arquivoProcessamento);
            } catch (RuntimeException erroMovimentacao) {
                causa.addSuppressed(erroMovimentacao);

                log.error("Falha ao devolver arquivo do processamento %d ao diretório de erros."
                        .formatted(idProcessamentoArquivo), erroMovimentacao);

                return;
            }
        }

        boolean cancelado = processamentoArquivoRepository.cancelarReservaTentativa(idProcessamentoArquivo, idTentativa,
                caminhoErro.toString(), "Falha ao preparar ou iniciar o Job: %s".formatted(causa.getMessage()));

        if (!cancelado) {
            log.error("Não foi possível cancelar a reserva da tentativa {} "
                    + "do processamento %d.".formatted(idTentativa), idProcessamentoArquivo);
        }
    }

    private JobParameters construirParametrosJob(Path arquivo, Long idProcessamentoArquivo, Long idCargaLogica,
            UUID idTentativa) {

        return new JobParametersBuilder()
                .addString("filePath", arquivo.toString(), false)
                .addLong("fileProcessingId", idProcessamentoArquivo)
                .addLong("logicalLoadId", idCargaLogica, false)
                .addString("attemptId", idTentativa.toString(), false)
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

    private void validarAutorizacaoInstancia() {

        if (!monitoramentoInstanciaService.instanciaAutorizada()) {

            throw new IllegalStateException(
                    "A instância FilePulse não está autorizada a iniciar novos processamentos.");
        }
    }

}
