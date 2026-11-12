package br.com.jards.filepulse.batch.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.model.CargaLogica;
import br.com.jards.filepulse.batch.model.MetadadosArquivoCsv;
import br.com.jards.filepulse.batch.repository.CargaLogicaRepository;

@Service
public class CargaLogicaService {

    private final CargaLogicaRepository repository;

    public CargaLogicaService(CargaLogicaRepository repository) {
        this.repository = repository;
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

}
