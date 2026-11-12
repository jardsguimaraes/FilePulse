package br.com.jards.filepulse.batch.repository;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.batch.enums.StatusCargaLogica;
import br.com.jards.filepulse.batch.model.CargaLogica;

@Repository
public class CargaLogicaRepository {

    private final JdbcTemplate jdbcTemplate;

    public CargaLogicaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CargaLogica> buscarPorIdCarga(String idCarga) {
        String sql = """
                    SELECT
                        id,
                        load_identifier,
                        expected_records,
                        status
                    FROM logical_load
                    WHERE load_identifier = ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new CargaLogica(
                        rs.getLong("id"),
                        rs.getString("load_identifier"),
                        rs.getLong("expected_records"),
                        StatusCargaLogica.valueOf(rs.getString("status"))),
                idCarga)
                .stream()
                .findFirst();
    }

    public CargaLogica criar(String idCarga, long totalRegistros) {
        String sql = """
                    INSERT INTO logical_load (
                        load_identifier,
                        expected_records,
                        status)
                    VALUES (?, ?, 'INCOMPLETA')
                    RETURNING id
                """;

        Long id = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                idCarga,
                totalRegistros);

        return new CargaLogica(
                id,
                idCarga,
                totalRegistros,
                StatusCargaLogica.INCOMPLETA);
    }
}
