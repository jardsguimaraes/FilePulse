package br.com.jards.filepulse.batch.repository;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.batch.enums.StatusProcessamentoArquivo;
import br.com.jards.filepulse.batch.enums.TipoArquivoCsv;
import br.com.jards.filepulse.batch.exception.TransicaoStatusInvalidaException;
import br.com.jards.filepulse.batch.model.CandidatoProcessamentoAbandonado;
import br.com.jards.filepulse.batch.model.ProcessamentoArquivo;

@Repository
public class ProcessamentoArquivoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessamentoArquivoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long criar(
            String nomeArquivo,
            String caminhoArquivo,
            String hashArquivo,
            Long idCargaLogica,
            TipoArquivoCsv tipoArquivo,
            UUID idTentativa) {

        Objects.requireNonNull(idCargaLogica,
                "O ID da carga lógica é obrigatório");

        Objects.requireNonNull(tipoArquivo,
                "O tipo do arquivo é obrigatório");

        Objects.requireNonNull(idTentativa,
                "O identificador da tentativa é obrigatório");

        String sql = """
                INSERT INTO file_processing (
                    file_name,
                    file_path,
                    file_hash,
                    status,
                    logical_load_id,
                    file_type,
                    active_attempt_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder chaveGerada = new GeneratedKeyHolder();

        jdbcTemplate.update(conexao -> {

            PreparedStatement declaracao = conexao.prepareStatement(
                    sql,
                    new String[] { "id" });

            declaracao.setString(1, nomeArquivo);
            declaracao.setString(2, caminhoArquivo);
            declaracao.setString(3, hashArquivo);
            declaracao.setString(4,
                    StatusProcessamentoArquivo.PROCESSANDO.name());
            declaracao.setLong(5, idCargaLogica);
            declaracao.setString(6, tipoArquivo.name());
            declaracao.setObject(7, idTentativa);

            return declaracao;

        }, chaveGerada);

        return Objects.requireNonNull(
                chaveGerada.getKey(),
                "O banco não retornou o ID do processamento")
                .longValue();
    }

    public void finalizar(
            Long id,
            UUID idTentativa,
            StatusProcessamentoArquivo status,
            Long quantidadeLida,
            Long quantidadeGravada,
            Long quantidadeIgnorada,
            String mensagemErro,
            String caminhoFinalArquivo) {

        Objects.requireNonNull(idTentativa, "O identificador da tentativa é obrigatório");

        Objects.requireNonNull(status, "O status final é obrigatório");

        if (status != StatusProcessamentoArquivo.COMPLETO
                && status != StatusProcessamentoArquivo.COMPLETO_COM_FALHAS
                && status != StatusProcessamentoArquivo.FALHOU
                && status != StatusProcessamentoArquivo.REJEITADO) {

            throw new IllegalArgumentException("Status não permitido para finalização: %s".formatted(status));
        }

        String sql = """
                UPDATE file_processing
                SET status = ?,
                    read_count = ?,
                    write_count = ?,
                    skip_count = ?,
                    finished_at = CURRENT_TIMESTAMP,
                    error_message = ?,
                    final_file_path = ?
                WHERE id = ?
                    AND status = ?
                    AND finished_at IS NULL
                    AND active_attempt_id = ?
                """;

        int linhasAtualizadas = jdbcTemplate.update(
                sql,
                status.name(),
                quantidadeLida,
                quantidadeGravada,
                quantidadeIgnorada,
                mensagemErro,
                caminhoFinalArquivo,
                id,
                StatusProcessamentoArquivo.PROCESSANDO.name(),
                idTentativa);

        if (linhasAtualizadas == 0) {
            throw new TransicaoStatusInvalidaException(
                    "Não foi possível finalizar o processamento %d. "
                            .formatted(id)
                            + "O processamento não está ativo ou a tentativa informada não é a tentativa atual.");
        }
    }

    public boolean existePorHash(String hashArquivo) {
        String sql = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM file_processing
                        WHERE file_hash = ?
                    )
                """;

        Boolean existe = jdbcTemplate.queryForObject(sql, Boolean.class, hashArquivo);

        return Boolean.TRUE.equals(existe);
    }

    private ProcessamentoArquivo mapearLinha(ResultSet rs, int numeroLinha) throws SQLException {
        var finalizadoEm = rs.getTimestamp("finished_at");
        return new ProcessamentoArquivo(
                rs.getLong("id"),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("final_file_path"),
                rs.getString("file_hash"),
                StatusProcessamentoArquivo.valueOf(rs.getString("status")),
                rs.getLong("read_count"),
                rs.getLong("write_count"),
                rs.getLong("skip_count"),
                rs.getTimestamp("started_at").toLocalDateTime(),
                finalizadoEm != null
                        ? finalizadoEm.toLocalDateTime()
                        : null,
                rs.getString("error_message"));
    }

    public List<CandidatoProcessamentoAbandonado> buscarCandidatosAbandonados(LocalDateTime iniciadoAntesDe) {

        Objects.requireNonNull(iniciadoAntesDe, "A data limite é obrigatória");

        String sql = """
                SELECT
                    id,
                    active_attempt_id,
                    file_path,
                    started_at
                FROM file_processing
                WHERE status = ?
                    AND finished_at IS NULL
                    AND started_at < ?
                    AND active_attempt_id IS NOT NULL
                ORDER BY started_at
                """;

        return jdbcTemplate.query(
                sql,
                (rs, numeroLinha) -> new CandidatoProcessamentoAbandonado(
                        rs.getLong("id"),
                        rs.getObject("active_attempt_id", UUID.class),
                        Path.of(rs.getString("file_path")),
                        rs.getTimestamp("started_at").toLocalDateTime()),
                StatusProcessamentoArquivo.PROCESSANDO.name(),
                Timestamp.valueOf(iniciadoAntesDe));
    }

    public boolean marcarComoAbandonado(Long id, UUID idTentativa, String mensagemErro) {

        Objects.requireNonNull(id, "O ID do processamento é obrigatório");

        Objects.requireNonNull(idTentativa, "O identificador da tentativa é obrigatório");

        String sql = """
                    UPDATE file_processing
                SET status = ?,
                    finished_at = CURRENT_TIMESTAMP,
                    error_message = ?,
                    final_file_path = NULL
                WHERE id = ?
                    AND status = ?
                    AND active_attempt_id = ?
                    AND finished_at IS NULL
                    """;

        int linhasAtualizadas = jdbcTemplate.update(
                sql,
                StatusProcessamentoArquivo.ABANDONADO.name(),
                mensagemErro,
                id,
                StatusProcessamentoArquivo.PROCESSANDO.name(),
                idTentativa);

        return linhasAtualizadas == 1;
    }

    public boolean atualizarCaminhoFinalArquivo(Long id, String caminhoFinalArquivo,
            StatusProcessamentoArquivo status) {
        String sql = """
                    UPDATE file_processing
                    SET final_file_path = ?
                    WHERE id = ?
                        AND status = ?
                        AND final_file_path IS NULL
                """;

        var linhasAtualizadas = jdbcTemplate.update(sql, caminhoFinalArquivo, id, status.name());

        return linhasAtualizadas == 1;
    }

    public Optional<ProcessamentoArquivo> buscarPorCaminhoArquivo(String caminhoArquivo) {
        String sql = """
                    SELECT
                        id,
                        file_name,
                        file_path,
                        final_file_path,
                        file_hash,
                        status,
                        read_count,
                        write_count,
                        skip_count,
                        started_at,
                        finished_at,
                        error_message
                    FROM file_processing
                    WHERE file_path = ?
                """;

        return jdbcTemplate.query(sql, this::mapearLinha, caminhoArquivo)
                .stream()
                .findFirst();
    }

    public Optional<ProcessamentoArquivo> buscarPorId(Long id) {
        String sql = """
                SELECT
                    id,
                    file_name,
                    file_path,
                    final_file_path,
                    file_hash,
                    status,
                    read_count,
                    write_count,
                    skip_count,
                    started_at,
                    finished_at,
                    error_message
                FROM file_processing
                WHERE id = ?
                """;

        return jdbcTemplate.query(sql, this::mapearLinha, id)
                .stream()
                .findFirst();
    }

    public boolean marcarComoEmProcessamento(Long id, String caminhoArquivo) {
        String sql = """
                UPDATE file_processing
                SET status = ?,
                    file_path = ?,
                    finished_at = NULL,
                    error_message = NULL,
                    final_file_path = NULL
                WHERE id = ?
                    AND status IN (?, ?)
                """;

        var linhasAtualizadas = jdbcTemplate.update(
                sql,
                StatusProcessamentoArquivo.PROCESSANDO.name(),
                caminhoArquivo,
                id,
                StatusProcessamentoArquivo.PROCESSANDO.name(),
                StatusProcessamentoArquivo.FALHOU.name());

        return linhasAtualizadas == 1;
    }

    public List<ProcessamentoArquivo> listarTodos(int pagina, int tamanho) {
        String sql = """
                SELECT
                    id,
                    file_name,
                    file_path,
                    final_file_path,
                    file_hash,
                    status,
                    read_count,
                    write_count,
                    skip_count,
                    started_at,
                    finished_at,
                    error_message
                FROM file_processing
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                    """;

        int deslocamento = Math.multiplyExact(pagina, tamanho);

        return jdbcTemplate.query(sql, this::mapearLinha, tamanho, deslocamento);
    }

    public long contarTodos() {
        String sql = """
                SELECT COUNT(*)
                FROM file_processing
                    """;

        Long total = jdbcTemplate.queryForObject(sql, Long.class);

        return Objects.requireNonNull(total, "A consulta de contagem não retornou um valor");
    }

    public Long buscarIdCargaLogica(Long idProcessamentoArquivo) {
        return jdbcTemplate.queryForObject(
                """
                            SELECT logical_load_id
                            FROM file_processing
                            WHERE id = ?
                        """,
                Long.class,
                idProcessamentoArquivo);
    }

    public boolean reservarTentativa(Long idProcessamentoArquivo, UUID idTentativa, String caminhoArquivo) {
        Objects.requireNonNull(idTentativa, "O identificador da tentativa é obrigatório");

        String sql = """
                UPDATE file_processing
                    SET active_attempt_id = ?,
                        status = ?,
                        file_path = ?,
                        started_at = CURRENT_TIMESTAMP,
                        finished_at = NULL,
                        error_message = NULL,
                        final_file_path = NULL
                    WHERE id = ?
                        AND status IN (?, ?)
                """;

        int linhasAtualizadas = jdbcTemplate.update(
                sql,
                idTentativa,
                StatusProcessamentoArquivo.PROCESSANDO.name(),
                caminhoArquivo,
                idProcessamentoArquivo,
                StatusProcessamentoArquivo.FALHOU.name(),
                StatusProcessamentoArquivo.ABANDONADO.name());

        return linhasAtualizadas == 1;
    }

    public boolean tentativaEstaAtiva(Long idProcessamentoArquivo, UUID idTentativa) {
        Objects.requireNonNull(idProcessamentoArquivo, "O ID do processamento é obrigatório");
        Objects.requireNonNull(idTentativa, "O identificador da tentativa é obrigatório");

        String sql = """
                SELECT COUNT(*)
                    FROM file_processing
                    WHERE id = ?
                        AND active_attempt_id = ?
                        AND status = ?
                        AND finished_at IS NULL
                """;

        Long quantidade = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                idProcessamentoArquivo,
                idTentativa,
                StatusProcessamentoArquivo.PROCESSANDO.name());

        return quantidade != null && quantidade == 1;
    }
}
