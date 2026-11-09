package br.com.jards.filepulse.watcher;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.batch.service.EstabilidadeArquivoService;
import br.com.jards.filepulse.batch.service.IdempotenciaArquivoService;
import br.com.jards.filepulse.batch.service.JobService;
import br.com.jards.filepulse.batch.service.MovimentacaoArquivoService;
import br.com.jards.filepulse.batch.service.ValidacaoArquivoService;
import br.com.jards.filepulse.watcher.enums.MotivoRejeicaoArquivo;
import br.com.jards.filepulse.watcher.service.RejeicaoArquivoService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class ManipuladorEventoArquivo {

    private final JobService jobService;
    private final ValidacaoArquivoService validacaoArquivoService;
    private final MovimentacaoArquivoService movimentacaoArquivoService;
    private final IdempotenciaArquivoService idempotenciaArquivoService;
    private final EstabilidadeArquivoService estabilidadeArquivoService;
    private final RejeicaoArquivoService rejeicaoArquivoService;

    public ManipuladorEventoArquivo(ValidacaoArquivoService validacaoArquivoService,
            MovimentacaoArquivoService movimentacaoArquivoService,
            JobService jobService, IdempotenciaArquivoService idempotenciaArquivoService,
            EstabilidadeArquivoService estabilidadeArquivoService, RejeicaoArquivoService rejeicaoArquivoService) {
        this.validacaoArquivoService = validacaoArquivoService;
        this.movimentacaoArquivoService = movimentacaoArquivoService;
        this.jobService = jobService;
        this.idempotenciaArquivoService = idempotenciaArquivoService;
        this.estabilidadeArquivoService = estabilidadeArquivoService;
        this.rejeicaoArquivoService = rejeicaoArquivoService;
    }

    public void aoCriarArquivo(Path arquivo) {
        log.info("Arquivo detectado: {}", arquivo);

        if (!validacaoArquivoService.ehValido(arquivo)) {
            rejeicaoArquivoService.rejeitar(arquivo, null, MotivoRejeicaoArquivo.FORMATO_INVALIDO,
                    "Formato de arquivo inválido");
            return;
        }

        log.info("Arquivo csv válido: {}", arquivo);

        if (!estabilidadeArquivoService.aguardarEstabilizacao(arquivo)) {
            rejeicaoArquivoService.rejeitar(arquivo, null, MotivoRejeicaoArquivo.ARQUIVO_INSTAVEL,
                    "Arquivo não estabilizou após o limite de tentativas");
            return;
        }

        if (!validacaoArquivoService.possuiCabecalhoValido(arquivo)) {
            rejeicaoArquivoService.rejeitar(arquivo, null, MotivoRejeicaoArquivo.CABECALHO_INVALIDO,
                    "Cabeçalho do arquivo inválido");
            return;
        }

        var resultadoIdempotencia = idempotenciaArquivoService.verificar(arquivo);

        if (resultadoIdempotencia.duplicado()) {
            rejeicaoArquivoService.rejeitar(arquivo, resultadoIdempotencia.hashArquivo(),
                    MotivoRejeicaoArquivo.DUPLICADO,
                    "Arquivo duplicado detectado antes do processamento");

            return;
        }

        var hashArquivo = resultadoIdempotencia.hashArquivo();

        log.info("Arquivo pronto para processamento {}", arquivo);

        var arquivoMovido = movimentacaoArquivoService.moverParaProcessamento(arquivo);
        var jobIniciado = jobService.iniciar(arquivoMovido, hashArquivo);

        if (!jobIniciado) {
            rejeicaoArquivoService.rejeitar(arquivoMovido, hashArquivo, MotivoRejeicaoArquivo.DUPLICADO,
                    "Arquivo duplicado detectado durante o início do processamento");
        }
    }
}
