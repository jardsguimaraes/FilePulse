
package br.com.jards.filepulse.batch.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;

@Service
public class AutorizacaoProcessamentoService {

    private final MonitoramentoInstanciaService monitoramentoInstanciaService;
    private final IdentidadeInstanciaService identidadeInstanciaService;
    private final ProcessamentoArquivoRepository processamentoArquivoRepository;

    public AutorizacaoProcessamentoService(
            MonitoramentoInstanciaService monitoramentoInstanciaService,
            IdentidadeInstanciaService identidadeInstanciaService,
            ProcessamentoArquivoRepository processamentoArquivoRepository) {

        this.monitoramentoInstanciaService = monitoramentoInstanciaService;
        this.identidadeInstanciaService = identidadeInstanciaService;
        this.processamentoArquivoRepository = processamentoArquivoRepository;
    }

    public void validarAutorizacao(Long idProcessamentoArquivo, UUID idTentativa) {

        Objects.requireNonNull(
                idProcessamentoArquivo,
                "O identificador do processamento é obrigatório");

        Objects.requireNonNull(
                idTentativa,
                "O identificador da tentativa é obrigatório");

        if (!TransactionSynchronizationManager
                .isActualTransactionActive()) {

            throw new IllegalStateException(
                    "A validação da tentativa deve ocorrer "
                            + "dentro de uma transação ativa do Spring Batch.");
        }

        if (!monitoramentoInstanciaService.instanciaAutorizada()) {

            throw new IllegalStateException(
                    "A instância FilePulse não está autorizada "
                            + "a continuar o processamento.");
        }

        UUID idInstancia = identidadeInstanciaService.obterIdInstancia();

        boolean tentativaAutorizada = processamentoArquivoRepository.validarTentativaComBloqueio(
                idProcessamentoArquivo,
                idTentativa,
                idInstancia);

        if (!tentativaAutorizada) {

            throw new IllegalStateException(
                    "Tentativa sem autorização persistente. "
                            + "Processamento: %d, tentativa: %s"
                                    .formatted(
                                            idProcessamentoArquivo,
                                            idTentativa));
        }
    }

}
