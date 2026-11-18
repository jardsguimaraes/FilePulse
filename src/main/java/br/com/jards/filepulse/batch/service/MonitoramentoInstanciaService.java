package br.com.jards.filepulse.batch.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.com.jards.filepulse.batch.enums.EstadoAutorizacaoInstancia;
import br.com.jards.filepulse.batch.repository.InstanciaFilePulseRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class MonitoramentoInstanciaService {

    private final IdentidadeInstanciaService identidadeInstanciaService;
    private final InstanciaFilePulseRepository instanciaRepository;

    private final AtomicReference<EstadoAutorizacaoInstancia> estadoAutorizacao = new AtomicReference<>(
            EstadoAutorizacaoInstancia.NAO_REGISTRADA);

    private volatile boolean registrada = false;

    public MonitoramentoInstanciaService(IdentidadeInstanciaService identidadeInstanciaService,
            InstanciaFilePulseRepository instanciaRepository) {

        this.identidadeInstanciaService = identidadeInstanciaService;
        this.instanciaRepository = instanciaRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarMonitoramento() {

        UUID idInstancia = identidadeInstanciaService.obterIdInstancia();

        instanciaRepository.registrarInstancia(idInstancia);

        estadoAutorizacao.set(EstadoAutorizacaoInstancia.AUTORIZADA);

        registrada = true;

        log.info("Instância FilePulse registrada: {}", idInstancia);
    }

    @Scheduled(fixedDelay = 10000)
    public void atualizarHeartbeat() {

        if (!registrada) {
            return;
        }

        UUID idInstancia = identidadeInstanciaService.obterIdInstancia();

        try {

            boolean atualizado = instanciaRepository.atualizarHeartbeat(idInstancia);

            if (!atualizado) {

                estadoAutorizacao.set(EstadoAutorizacaoInstancia.AUTORIZACAO_PERDIDA);

                log.error("Autorização da instância perdida. Instância: {}", idInstancia);
            }

        } catch (RuntimeException e) {
            estadoAutorizacao.set(EstadoAutorizacaoInstancia.AUTORIZACAO_PERDIDA);
            log.error("Falha ao atualizar heartbeat da instância {}", idInstancia, e);
        }
    }

    @PreDestroy
    public void encerrarMonitoramento() {

        if (!registrada) {
            return;
        }

        UUID idInstancia = identidadeInstanciaService.obterIdInstancia();

        try {

            boolean encerrada = instanciaRepository.registrarEncerramento(idInstancia);

            if (encerrada) {
                log.info("Encerramento da instância registrado: {}", idInstancia);
            }

        } catch (RuntimeException e) {

            log.error("Não foi possível registrar o encerramento da instância {}", idInstancia, e);
        }
    }

    public boolean instanciaAutorizada() {

        return estadoAutorizacao.get() == EstadoAutorizacaoInstancia.AUTORIZADA;
    }

}
