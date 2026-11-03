package br.com.jards.filepulse.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.jards.filepulse.api.dto.ProcessamentoArquivoResponse;
import br.com.jards.filepulse.api.dto.PaginaResponse;
import br.com.jards.filepulse.api.service.ProcessamentoArquivoService;

@RestController
@RequestMapping("/api/v1/files")
public class ProcessamentoArquivoController {

    private final ProcessamentoArquivoService service;

    public ProcessamentoArquivoController(ProcessamentoArquivoService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessamentoArquivoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<PaginaResponse<ProcessamentoArquivoResponse>> listarTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(service.listarTodos(page, size));
    }
}
