package br.com.jards.filepulse.batch.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record EstadoInstanciaFilePulse(
        UUID idInstancia,
        LocalDateTime iniciadaEm,
        LocalDateTime ultimoHeartbeatEm,
        LocalDateTime encerradaEm) {
}
