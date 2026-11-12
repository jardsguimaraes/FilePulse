package br.com.jards.filepulse.batch.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.model.CargaLogica;

@JdbcTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CargaLogicaRepository.class)
class CargaLogicaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CargaLogicaRepository repository;

    @Test
    void deveCriarCargaLogica() {

        CargaLogica carga = repository.criar(
                "CAIXA-20261112-000001",
                100000L);

        assertNotNull(carga.id());
        assertEquals(
                "CAIXA-20261112-000001",
                carga.idCarga());
        assertEquals(100000L, carga.totalRegistros());
        assertEquals(
                StatusCargaLogica.INCOMPLETA,
                carga.status());
    }

    @Test
    void deveBuscarCargaExistente() {

        CargaLogica criada = repository.criar(
                "CAIXA-20261112-000002",
                50000L);

        CargaLogica encontrada = repository
                .buscarPorIdCarga(criada.idCarga())
                .orElseThrow();

        assertEquals(criada.id(), encontrada.id());
        assertEquals(criada.idCarga(), encontrada.idCarga());
        assertEquals(
                criada.totalRegistros(),
                encontrada.totalRegistros());
        assertEquals(
                StatusCargaLogica.INCOMPLETA,
                encontrada.status());
    }

    @Test
    void deveRetornarOptionalVazioQuandoCargaNaoExiste() {

        var resultado = repository.buscarPorIdCarga(
                "CAIXA-20261112-999999");

        assertTrue(resultado.isEmpty());
    }

    @Test
    void deveRejeitarIdentificadorDuplicado() {

        String idCarga = "CAIXA-20261112-000003";

        repository.criar(idCarga, 100000L);

        assertThrows(
                DuplicateKeyException.class,
                () -> repository.criar(idCarga, 100000L));
    }
}
