package br.com.jards.filepulse.batch.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessamentoErroRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessamentoErroRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrar(Long idProcessamentoArquivo, Long numeroLinha, String etapaErro, String tipoErro,
            String mensagemErro, String conteudoOriginal) {

        String sql = """
                INSERT INTO file_processing_error (
                    file_processing_id,
                    line_number,
                    error_stage,
                    error_type,
                    error_message,
                    raw_content)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                idProcessamentoArquivo,
                numeroLinha,
                etapaErro,
                tipoErro,
                mensagemErro,
                conteudoOriginal);
    }

    public List<Map<String, Object>> buscarPorProcessamento(Long idProcessamentoArquivo) {

        String sql = """
                    SELECT
                        id,
                        file_processing_id,
                        line_number,
                        error_stage,
                        error_type,
                        error_message,
                        raw_content,
                        created_at
                    FROM file_processing_error
                    WHERE file_processing_id = ?
                    ORDER BY line_number NULLS LAST, id
                """;

        return jdbcTemplate.queryForList(sql, idProcessamentoArquivo);
    }

    public long contarPorProcessamento(Long idProcessamentoArquivo) {
        Long quantidade = jdbcTemplate.queryForObject(
                """
                            SELECT COUNT(*)
                            FROM file_processing_error
                            WHERE file_processing_id = ?
                        """,
                Long.class,
                idProcessamentoArquivo);

                return  quantidade != null ? quantidade : 0L;
    }
}
