package br.com.jards.filepulse.batch.service;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.model.CargaLogica;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;
import br.com.jards.filepulse.batch.model.ReservaProcessamento;
import br.com.jards.filepulse.batch.repository.CargaLogicaRepository;
import br.com.jards.filepulse.batch.repository.ProcessamentoArquivoRepository;
import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;

@Service
public class CargaLogicaService {

    private final ArmazenamentoArquivoProperties armazenamentoArquivoProperties;
    private final CargaLogicaRepository repository;
    private final ProcessamentoArquivoRepository processamentoArquivoRepository;

    public CargaLogicaService(CargaLogicaRepository repository,
            ArmazenamentoArquivoProperties armazenamentoArquivoProperties,
            ProcessamentoArquivoRepository processamentoArquivoRepository) {
        this.repository = repository;
        this.armazenamentoArquivoProperties = armazenamentoArquivoProperties;
        this.processamentoArquivoRepository = processamentoArquivoRepository;
    }

    @Transactional
    public CargaLogica prepararCarga(MetadadosArquivoCsv metadados) {
        Objects.requireNonNull(metadados, "Os metadados do arquivo são obrigatórios");

        if (TipoArquivoCsv.ORIGINAL.equals(metadados.tipoArquivo())) {
            return prepararOriginal(metadados);
        }

        if (TipoArquivoCsv.COMPLEMENTAR.equals(metadados.tipoArquivo())) {
            return prepararComplementar(metadados);
        }

        throw new IllegalArgumentException("Tipo de arquivo não suportado: %s".formatted(metadados.tipoArquivo()));
    }

    private CargaLogica prepararComplementar(MetadadosArquivoCsv metadados) {
        CargaLogica carga = repository.buscarPorIdCarga(metadados.idCarga())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Carga lógica não encontrada: %s".formatted(metadados.idCarga())));

        if (carga.status() != StatusCargaLogica.INCOMPLETA) {
            throw new IllegalArgumentException(
                    "A carga lógica não está disponível para complementação: %s. Status atual: %s"
                            .formatted(metadados.idCarga(), carga.status()));
        }

        if (carga.totalRegistros() != metadados.totalRegistros()) {
            throw new IllegalArgumentException(
                    "Total de registros divergente para a carga %s. Esperado: %d, informado: %d"
                            .formatted(metadados.idCarga(), carga.totalRegistros(), metadados.totalRegistros()));
        }

        return carga;
    }

    private CargaLogica prepararOriginal(MetadadosArquivoCsv metadados) {
        repository.buscarPorIdCarga(metadados.idCarga())
                .ifPresent(carga -> {
                    throw new IllegalArgumentException(
                            "Já existe uma carga com o identificador: %s".formatted(metadados.idCarga()));
                });

        return repository.criar(metadados.idCarga(), metadados.totalRegistros());
    }

    @Transactional
    public boolean conciliarCarga(Long idCargaLogica) {
        Objects.requireNonNull(idCargaLogica, "O ID da carga lógica é obrigatório");

        repository.bloquearCarga(idCargaLogica);

        return repository.concluirSeCompleta(idCargaLogica);
    }

    @Transactional
    public ReservaProcessamento prepararCargaEReservarProcessamento(MetadadosArquivoCsv metadados, Path arquivo,
            String hashArquivo) {

        Objects.requireNonNull(metadados);
        Objects.requireNonNull(arquivo);
        Objects.requireNonNull(hashArquivo);

        CargaLogica carga;

        if (metadados.tipoArquivo() == TipoArquivoCsv.ORIGINAL) {
            carga = prepararOriginal(metadados);
        } else if (metadados.tipoArquivo() == TipoArquivoCsv.COMPLEMENTAR) {
            var cargaExistente = repository.buscarPorIdCarga(metadados.idCarga())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Carga lógica não encontrada: %s".formatted(metadados.idCarga())));

            repository.bloquearCarga(cargaExistente.id());

            carga = prepararComplementar(metadados);
        } else {
            throw new IllegalArgumentException("Tipo de arquivo não suportado: %s".formatted(metadados.tipoArquivo()));
        }

        Long idProcessamentoArquivo = processamentoArquivoRepository.criar(
                arquivo.getFileName().toString(),
                arquivo.toString(),
                hashArquivo,
                carga.id(),
                metadados.tipoArquivo());

        return new ReservaProcessamento(idProcessamentoArquivo, carga.id());
    }
}
