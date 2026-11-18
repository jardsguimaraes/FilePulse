package br.com.jards.filepulse.batch.repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.batch.model.EstadoInstanciaFilePulse;

@Repository
public class InstanciaFilePulseRepository {

    private final JdbcTemplate jdbcTemplate;

    public InstanciaFilePulseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrarInstancia(UUID idInstancia) {

        Objects.requireNonNull(idInstancia, "O identificador da instância é obrigatório");

        String sql = """
                INSERT INTO filepulse_instance (
                    instance_id,
                    started_at,
                    last_heartbeat_at
                )
                VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;

        jdbcTemplate.update(sql, idInstancia);
    }

    public boolean atualizarHeartbeat(UUID idInstancia) {

        Objects.requireNonNull(idInstancia, "O identificador da instância é obrigatório");

        String sql = """
                UPDATE filepulse_instance
                SET last_heartbeat_at = CURRENT_TIMESTAMP
                WHERE instance_id = ?
                    AND stopped_at IS NULL
                """;

        return jdbcTemplate.update(sql, idInstancia) == 1;
    }

    public boolean registrarEncerramento(UUID idInstancia) {

        Objects.requireNonNull(idInstancia, "O identificador da instância é obrigatório");

        String sql = """
                UPDATE filepulse_instance
                SET stopped_at = CURRENT_TIMESTAMP
                WHERE instance_id = ?
                    AND stopped_at IS NULL
                """;

        return jdbcTemplate.update(sql, idInstancia) == 1;
    }

    public Optional<EstadoInstanciaFilePulse> buscarPorId(
            UUID idInstancia) {

        Objects.requireNonNull(idInstancia, "O identificador da instância é obrigatório");

        String sql = """
                SELECT
                    instance_id,
                    started_at,
                    last_heartbeat_at,
                    stopped_at
                FROM filepulse_instance
                WHERE instance_id = ?
                """;

        return jdbcTemplate.query(
                sql,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(
                            new EstadoInstanciaFilePulse(
                                    rs.getObject("instance_id", UUID.class),
                                    rs.getTimestamp("started_at").toLocalDateTime(),
                                    rs.getTimestamp("last_heartbeat_at").toLocalDateTime(),
                                    rs.getTimestamp("stopped_at") == null
                                            ? null
                                            : rs.getTimestamp("stopped_at").toLocalDateTime()));
                },
                idInstancia);
    }

}
