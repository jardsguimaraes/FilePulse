package br.com.jards.filepulse.batch.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.config.FileProcessingRecoveryProperties;
import br.com.jards.filepulse.batch.repository.FileProcessingRepository;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileProcessingRecoveryService {

    private final FileProcessingRepository fileProcessingRepository;
    private final FileProcessingRecoveryProperties recoveryProperties;
    private final FileMovementService fileMovementService;

    public FileProcessingRecoveryService(FileProcessingRepository fileProcessingRepository,
            FileProcessingRecoveryProperties recoveryProperties, FileMovementService fileMovementService) {
        this.fileProcessingRepository = fileProcessingRepository;
        this.recoveryProperties = recoveryProperties;
        this.fileMovementService = fileMovementService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void detectAbandonedProcessing() {
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
