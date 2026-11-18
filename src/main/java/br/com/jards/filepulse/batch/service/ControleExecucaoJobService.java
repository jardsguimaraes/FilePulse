package br.com.jards.filepulse.batch.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class ControleExecucaoJobService {

    private enum EstadoTentativa {
        PREPARANDO,
        EXECUTANDO
    }

    private final ConcurrentMap<UUID, EstadoTentativa> tentativas = new ConcurrentHashMap<>();

    public boolean iniciarPreparacao(UUID idTentativas) {
        return tentativas.putIfAbsent(idTentativas, EstadoTentativa.PREPARANDO) == null;
    }

    public boolean registrar(UUID idTentativa) {

        var resultado = new java.util.concurrent.atomic.AtomicBoolean(false);

        tentativas.compute(idTentativa, (id, estadoAtual) -> {

            if (estadoAtual == null || estadoAtual == EstadoTentativa.PREPARANDO) {
                resultado.set(true);
                return EstadoTentativa.EXECUTANDO;
            }

            return estadoAtual;
        });

        return resultado.get();
    }

    public void finalizar(UUID idTentativa) {
        tentativas.remove(idTentativa);
    }

    public boolean estaEmExecucao(UUID idTentativa) {
        return tentativas.containsKey(idTentativa);
    }
}
