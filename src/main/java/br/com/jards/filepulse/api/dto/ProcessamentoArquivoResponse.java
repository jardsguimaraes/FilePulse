package br.com.jards.filepulse.api.dto;

import java.time.LocalDateTime;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;

public record ProcessamentoArquivoResponse(
        Long id,
        String fileName,
        StatusProcessamentoArquivo status,
        Long readCount,
        Long writeCount,
        Long skipCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage) {

    public static ProcessamentoArquivoResponse from(ProcessamentoArquivo processamento) {
        return new ProcessamentoArquivoResponse(
                processamento.id(),
                processamento.nomeArquivo(),
                processamento.status(),
                processamento.quantidadeLida(),
                processamento.quantidadeGravada(),
                processamento.quantidadeIgnorada(),
                processamento.iniciadoEm(),
                processamento.finalizadoEm(),
                processamento.mensagemErro());
    }
}
