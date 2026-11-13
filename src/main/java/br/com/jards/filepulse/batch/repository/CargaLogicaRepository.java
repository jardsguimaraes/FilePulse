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

    // public long contarRegistrosPersistidos(Long idCargaLogica) {
    // String sql = """
    // SELECT COUNT(*)
    // FROM customer
    // WHERE logical_load_id = ?
    // """;

    // Long quantidade = jdbcTemplate.queryForObject(
    // sql,
    // Long.class,
    // idCargaLogica);

    // return Objects.requireNonNull(quantidade, "Não foi possível contar os
    // registros da carga lógica");
    // }

    public boolean concluirSeCompleta(Long idCargaLogica) {
        String sql = """
                    UPDATE logical_load
                    SET status = 'CONCLUIDA',
                        completed_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                        AND status = 'INCOMPLETA'
                        AND expected_records = (
                            SELECT COUNT(*)
                            FROM customer
                            WHERE logical_load_id = ?)
                        AND NOT EXISTS (
                            SELECT 1
                            FROM file_processing
                            WHERE logical_load_id = ?
                            AND status = 'PROCESSANDO')
                """;

        int linhasAtualizadas = jdbcTemplate.update(
                sql,
                idCargaLogica,
                idCargaLogica,
                idCargaLogica);

        return linhasAtualizadas == 1;
    }

    public void bloquearCarga(Long idCargaLogica) {
        String sql = """
                    SELECT id
                    FROM logical_load
                    WHERE id = ?
                    FOR UPDATE
                """;

        var ids = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("id"),
                idCargaLogica);

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Carga lógica não encontrada: %d".formatted(idCargaLogica));
        }
    }
}
