package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.config.RecuperacaoProcessamentoArquivoProperties;
import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.config.FileStorageProperties;
import br.com.jards.filepulse.watcher.enums.FileRejectionReason;
import br.com.jards.filepulse.watcher.service.FileRejectionService;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileProcessingRecoveryService {

    private final ProcessamentoArquivoRepository fileProcessingRepository;
    private final RecuperacaoProcessamentoArquivoProperties recoveryProperties;
    private final MovimentacaoArquivoService fileMovementService;
    private final FileStorageProperties fileStorageProperties;
    private final FileHashService fileHashService;
    private final FileRejectionService fileRejectionService;

    public FileProcessingRecoveryService(ProcessamentoArquivoRepository fileProcessingRepository,
            RecuperacaoProcessamentoArquivoProperties recoveryProperties,
            MovimentacaoArquivoService fileMovementService,
            FileStorageProperties fileStorageProperties, FileHashService fileHashService,
            FileRejectionService fileRejectionService) {
        this.fileProcessingRepository = fileProcessingRepository;
        this.recoveryProperties = recoveryProperties;
        this.fileMovementService = fileMovementService;
        this.fileStorageProperties = fileStorageProperties;
        this.fileHashService = fileHashService;
        this.fileRejectionService = fileRejectionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        recoverAbandonedProcessing();
        detectOrphanProcessingFiles();
    }

    private void detectOrphanProcessingFiles() {
        var processingDirectory = Path.of(fileStorageProperties.getProcessingDirectory());

        try (var files = Files.list(processingDirectory)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> {
                        fileProcessingRepository.buscarPorCaminhoArquivo(file.toString())
                                .ifPresentOrElse(processing -> reconcileExistingProcessing(file, processing),
                                        () -> reconcileOrphanFile(file));
                    });
        } catch (IOException e) {
            log.error("Não foi possível verificar arquivos órfãos no diretório de processamento: {}",
                    processingDirectory, e);
        }
    }

    private void reconcileOrphanFile(Path file) {
        var fileHash = fileHashService.calculate(file);

        var existsByHash = fileProcessingRepository.existePorHash(fileHash);

        var reason = existsByHash
                ? FileRejectionReason.ORPHAN_DUPLICATE
                : FileRejectionReason.ORPHAN_PROCESSING_FILE;

        var details = existsByHash
                ? "Arquivo órfão com conteúdo já existente no histórico de processamento"
                : "Arquivo encontrado no diretório de processamento sem registro correspondente";

        recoverOrphanFile(file, fileHash, reason, details);
    }

    private void reconcileExistingProcessing(Path file, ProcessamentoArquivo processando) {

        switch (processando.status()) {
            case COMPLETO -> reconcileCompletedProcessing(file, processando, true);
            case FALHOU, ABANDONADO -> reconcileCompletedProcessing(file, processando, false);
            default -> log.warn(
                    "Processamento existente ainda não possui estratégia de reconciliação. id: {}, status: {}, arquivo: {}",
                    processando.id(),
                    processando.status(),
                    file);
        }
    }

    private void reconcileCompletedProcessing(Path file, ProcessamentoArquivo processando, boolean sucessful) {
        var finalFile = sucessful
                ? fileMovementService.moverParaProcessados(file)
                : fileMovementService.moverParaErro(file);

        var update = fileProcessingRepository.atualizarCaminhoFinalArquivo(processando.id(), finalFile.toString(),
                StatusProcessamentoArquivo.COMPLETO);

        if (update) {
            log.warn("Processamento COMPLETO reconciliado. id: {}, origem: {}, destino: {}",
                    processando.id(),
                    file,
                    finalFile);
        } else {
            log.warn("Caminho final não foi atualizado durante reconciliação. id: {}, arquivo: {}",
                    processando.id(),
                    file);
        }
    }

    private void recoverOrphanFile(Path file, String fileHash, FileRejectionReason reason, String details) {
        var finalFile = fileMovementService.moverParaErro(file);

        fileRejectionService.audit(finalFile, fileHash, reason, details);

        log.warn("Arquivo órfão recuperado. origem: {}, destino: {}, motivo: {}", file, finalFile, reason);
    }

    private void recoverAbandonedProcessing() {
        var startedBefore = LocalDateTime.now().minusMinutes(recoveryProperties.getAbandonedAfterMinutes());
        var candidates = fileProcessingRepository.buscarCandidatosAbandonados(startedBefore);

        if (candidates.isEmpty()) {
            log.info("Nenhum processamento abandonado encontrado.");
            return;
        }

        log.warn("{} processamento(s) candidato(s) a abandono encontrado(s).", candidates.size());

        candidates.forEach(
                processing -> {
                    var file = Path.of(processing.caminhoArquivo());

                    if (Files.exists(file)) {
                        log.warn(
                                "Processamento abandonado com arquivo disponível. id: {}, arquivo: {}, iniciado em: {}",
                                processing.id(),
                                file,
                                processing.iniciadoEm());

                        var finalFile = fileMovementService.moverParaErro(file);
                        var updated = fileProcessingRepository.marcarComoAbandonado(
                                processing.id(),
                                "Processamento interrompido antes da finalização da aplicação",
                                finalFile.toString());

                        if (updated) {
                            log.warn("Processamento marcado como ABANDONED. id: {}, arquivo final: {}",
                                    processing.id(),
                                    finalFile);
                        } else {
                            log.warn(
                                    "Processamento não foi marcado como ABANDONED porque seu estado foi alterado. id: {}",
                                    processing.id());
                        }

                        return;
                    } else {
                        log.error(
                                "Processamento abandonado sem arquivo no caminho esperado. id: {}, arquivo: {}, iniciado em: {}",
                                processing.id(),
                                file,
                                processing.iniciadoEm());
                    }
                });
    }
}
