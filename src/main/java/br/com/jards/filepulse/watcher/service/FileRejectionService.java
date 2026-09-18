package br.com.jards.filepulse.watcher.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.service.FileMovementService;
import br.com.jards.filepulse.watcher.enums.FileRejectionReason;
import br.com.jards.filepulse.watcher.repositeries.FileRejectionRepository;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileRejectionService {

    private final FileRejectionRepository fileRejectionRepository;
    private final FileMovementService fileMovementService;

    public FileRejectionService(FileRejectionRepository fileRejectionRepository,
            FileMovementService fileMovementService) {
        this.fileRejectionRepository = fileRejectionRepository;
        this.fileMovementService = fileMovementService;
    }

    public void reject(Path file, String fileHash, FileRejectionReason reason, String details) {
        var rejectedFile = fileMovementService.moveToRejected(file);

        fileRejectionRepository.create(
                rejectedFile.getFileName().toString(),
                rejectedFile.toString(),
                fileHash,
                reason,
                details);

        log.warn("Arquivo rejeitado. Arquivo: {}, motivo: {}, detalhes: {}", rejectedFile, reason, details);
    }

}
