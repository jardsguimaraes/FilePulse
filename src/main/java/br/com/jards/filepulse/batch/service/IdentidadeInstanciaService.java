package br.com.jards.filepulse.batch.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class IdentidadeInstanciaService {

    private final UUID idInstancia = UUID.randomUUID();

    public UUID obterIdInstancia() {
        return idInstancia;
    }
}
