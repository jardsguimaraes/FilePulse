package br.com.jards.filepulse.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.jards.filepulse.api.dto.FileProcessingResponse;
import br.com.jards.filepulse.api.service.FileProcessingService;

@RestController
@RequestMapping("/api/v1/files")
public class FileProcessingController {

    private final FileProcessingService service;

    public FileProcessingController(FileProcessingService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileProcessingResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }
}
