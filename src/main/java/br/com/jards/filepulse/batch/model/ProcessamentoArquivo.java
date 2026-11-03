package br.com.jards.filepulse.batch.model;

import java.time.LocalDateTime;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;

public record ProcessamentoArquivo(
        Long id,
        String nomeArquivo,
        String caminhoArquivo,
        String caminhoFinalArquivo,
        String hashArquivo,
        StatusProcessamentoArquivo status,
        Long quantidadeLida,
        Long quantidadeGravada,
        Long quantidadeIgnorada,
        LocalDateTime iniciadoEm,
        LocalDateTime finalizadoEm,
        String mensagemErro) {

}