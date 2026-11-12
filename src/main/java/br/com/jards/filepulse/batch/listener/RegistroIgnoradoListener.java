package br.com.jards.filepulse.batch.listener;

import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.enums.EtapaErroProcessamento;
import br.com.jards.filepulse.batch.exception.RegistroInvalidoException;
import br.com.jards.filepulse.batch.model.Cliente;
import br.com.jards.filepulse.batch.model.RegistroClienteCsv;
import br.com.jards.filepulse.batch.repository.ProcessamentoErroRepository;
import lombok.extern.log4j.Log4j2;

@Component
@StepScope
@Log4j2
public class RegistroIgnoradoListener implements SkipListener<RegistroClienteCsv, Cliente> {

    private final ProcessamentoErroRepository processamentoErroRepository;
    private final Long idProcessamentoArquivo;

    public RegistroIgnoradoListener(ProcessamentoErroRepository processamentoErroRepository,
            @Value("#{jobParameters['fileProcessingId']}") Long idProcessamentoArquivo) {
        this.processamentoErroRepository = processamentoErroRepository;
        this.idProcessamentoArquivo = idProcessamentoArquivo;
    }

    @Override
    public void onSkipInProcess(RegistroClienteCsv registro, Throwable causa) {

        processamentoErroRepository.registrar(
                idProcessamentoArquivo,
                registro.numeroLinha(),
                EtapaErroProcessamento.PROCESSAMENTO.name(),
                causa.getClass().getSimpleName(),
                obterMensagemErro(causa),
                null);

        log.warn("Registro ignorado. Processamento: {}. Linha: {}. Motivo: {}",
                idProcessamentoArquivo,
                registro.numeroLinha(),
                obterMensagemErro(causa));
    }

    @Override
    public void onSkipInRead(Throwable causa) {

        Long numeroLinha = null;

        if (causa instanceof FlatFileParseException erroLeitura) {
            numeroLinha = (long) erroLeitura.getLineNumber();
        }

        String mensagemErro = obterMensagemErro(causa);

        processamentoErroRepository.registrar(
                idProcessamentoArquivo,
                numeroLinha,
                EtapaErroProcessamento.LEITURA.name(),
                causa.getClass().getSimpleName(),
                mensagemErro,
                null);

        log.warn(
                "Erro de leitura ignorado. Processamento: {}. Linha: {}. Motivo: {}",
                idProcessamentoArquivo,
                numeroLinha,
                mensagemErro);
    }

    @Override
    public void onSkipInWrite(Cliente cliente, Throwable causa) {
        processamentoErroRepository.registrar(
                idProcessamentoArquivo,
                null,
                EtapaErroProcessamento.ESCRITA.name(),
                causa.getClass().getSimpleName(),
                obterMensagemErro(causa),
                null);

        log.warn("Erro de escrita ignorado. Processamento: {}. Motivo: {}",
                idProcessamentoArquivo,
                obterMensagemErro(causa));
    }

    private String obterMensagemErro(Throwable causa) {
        if (causa == null) {
            return "Erro não identificado";
        }

        if (causa instanceof RegistroInvalidoException) {
            return causa.getMessage() != null
                    ? causa.getMessage()
                    : "Registro inválido";
        }

        return "Falha do tipo: %s".formatted(causa.getClass().getSimpleName());
    }
}
