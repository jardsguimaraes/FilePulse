package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.config.FileProcessingRecoveryProperties;
import br.com.jards.filepulse.batch.enums.FileProcessingStatus;
import br.com.jards.filepulse.batch.model.FileProcessing;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;
import br.com.jards.filepulse.watcher.config.FileStorageProperties;
import br.com.jards.filepulse.watcher.enums.FileRejectionReason;
import br.com.jards.filepulse.watcher.service.FileRejectionService;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileProcessingRecoveryService {

    private final FileProcessingRepository fileProcessingRepository;
    private final FileProcessingRecoveryProperties recoveryProperties;
    private final FileMovementService fileMovementService;
    private final FileStorageProperties fileStorageProperties;
    private final FileHashService fileHashService;
    private final FileRejectionService fileRejectionService;

    public FileProcessingRecoveryService(FileProcessingRepository fileProcessingRepository,
            FileProcessingRecoveryProperties recoveryProperties, FileMovementService fileMovementService,
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
                        fileProcessingRepository.findByFilePath(file.toString())
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

        var existsByHash = fileProcessingRepository.existsByHash(fileHash);

        var reason = existsByHash
                ? FileRejectionReason.ORPHAN_DUPLICATE
                : FileRejectionReason.ORPHAN_PROCESSING_FILE;

        var details = existsByHash
                ? "Arquivo órfão com conteúdo já existente no histórico de processamento"
                : "Arquivo encontrado no diretório de processamento sem registro correspondente";

        recoverOrphanFile(file, fileHash, reason, details);
    }

    private void reconcileExistingProcessing(Path file, FileProcessing processing) {

        switch (processing.status()) {
            case COMPLETED -> reconcileCompletedProcessing(file, processing, true);
            case FAILED, ABANDONED -> reconcileCompletedProcessing(file, processing, false);
            default -> log.warn(
                    "Processamento existente ainda não possui estratégia de reconciliação. id: {}, status: {}, arquivo: {}",
                    processing.id(),
                    processing.status(),
                    file);
        }
    }

    private void reconcileCompletedProcessing(Path file, FileProcessing processing, boolean sucessful) {
        var finalFile = sucessful
                ? fileMovementService.moveToProcessed(file)
                : fileMovementService.moveToError(file);

        var update = fileProcessingRepository.updateFinalFilePath(processing.id(), finalFile.toString(),
                FileProcessingStatus.COMPLETED);

        if (update) {
            log.warn("Processamento COMPLETED reconciliado. id: {}, origem: {}, destino: {}",
                    processing.id(),
                    file,
                    finalFile);
        } else {
            log.warn("Caminho final não foi atualizado durante reconciliação. id: {}, arquivo: {}",
                    processing.id(),
                    file);
        }
    }

    private void recoverOrphanFile(Path file, String fileHash, FileRejectionReason reason, String details) {
        var finalFile = fileMovementService.moveToError(file);

        fileRejectionService.audit(finalFile, fileHash, reason, details);

        log.warn("Arquivo órfão recuperado. origem: {}, destino: {}, motivo: {}", file, finalFile, reason);
    }

    private void recoverAbandonedProcessing() {
        var startedBefore = LocalDateTime.now().minusMinutes(recoveryProperties.getAbandonedAfterMinutes());
        var candidates = fileProcessingRepository.findAbandonedCandidates(startedBefore);

        if (candidates.isEmpty()) {
            log.info("Nenhum processamento abandonado encontrado.");
            return;
        }

        log.warn("{} processamento(s) candidato(s) a abandono encontrado(s).", candidates.size());

        candidates.forEach(
                processing -> {
                    var file = Path.of(processing.filePath());

                    if (Files.exists(file)) {
                        log.warn(
                                "Processamento abandonado com arquivo disponível. id: {}, arquivo: {}, iniciado em: {}",
                                processing.id(),
                                file,
                                processing.startedAt());

                        var finalFile = fileMovementService.moveToError(file);
                        var updated = fileProcessingRepository.markAsAbandoned(
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
                                processing.startedAt());
                    }
                });
    }
}
