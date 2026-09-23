package br.com.jards.filepulse.batch.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.batch.enums.FileProcessingStatus;
import br.com.jards.filepulse.batch.model.FileProcessing;

@Repository
public class FileProcessingRepository {

    private final JdbcTemplate jdbcTemplate;

    public FileProcessingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long create(String fileName, String filePath, String fileHash) {
        String sql = """
                    INSERT INTO file_processing (file_name, file_path, file_hash, status)
                VALUES (?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[] { "id" });

            statement.setString(1, fileName);
            statement.setString(2, filePath);
            statement.setString(3, fileHash);
            statement.setString(4, FileProcessingStatus.PROCESSING.name());

            return statement;
        }, keyHolder);

        return keyHolder.getKey().longValue();
    }

    public void finish(Long id, FileProcessingStatus status, Long readCount, Long writeCount, Long skipCount,
            String errorMessage, String finalFilePath) {
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
                """;

        jdbcTemplate.update(sql, status.name(), readCount, writeCount, skipCount, errorMessage, finalFilePath, id);
    }

    public boolean existsByHash(String fileHash) {
        String sql = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM file_processing
                        WHERE file_hash = ?
                    )
                """;

        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, fileHash);

        return Boolean.TRUE.equals(exists);
    }

    private FileProcessing mapRow(ResultSet rs, int rowNum) throws SQLException {
        var finishedAt = rs.getTimestamp("finished_at");
        return new FileProcessing(
                rs.getLong("id"),
                rs.getString("file_name"),
                rs.getString("file_path"),
                rs.getString("final_file_path"),
                rs.getString("file_hash"),
                FileProcessingStatus.valueOf(rs.getString("status")),
                rs.getLong("read_count"),
                rs.getLong("write_count"),
                rs.getLong("skip_count"),
                rs.getTimestamp("started_at").toLocalDateTime(),
                finishedAt != null
                        ? finishedAt.toLocalDateTime()
                        : null,
                rs.getString("error_message"));
    }

    public List<FileProcessing> findAbandonedCandidates(LocalDateTime startedBefore) {
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
                WHERE status = ?
                    AND finished_at IS NULL
                    AND started_at < ?
                ORDER BY started_at
                """;

        return jdbcTemplate.query(sql, this::mapRow, FileProcessingStatus.PROCESSING.name(),
                Timestamp.valueOf(startedBefore));
    }

    public boolean markAsAbandoned(Long id, String errorMessage, String finalFilePath) {
        String sql = """
                UPDATE file_processing
                SET status = ?,
                    finished_at = CURRENT_TIMESTAMP,
                    error_message = ?,
                    final_file_path = ?
                WHERE id = ?
                    AND status = ?
                    AND finished_at IS NULL
                """;

        var updatedRows = jdbcTemplate.update(sql, FileProcessingStatus.ABANDONED.name(), errorMessage, finalFilePath,
                id, FileProcessingStatus.PROCESSING.name());

        return updatedRows == 1;
    }

    public boolean updateFinalFilePath(Long id, String finalFilePath, FileProcessingStatus status) {
        String sql = """
                    UPDATE file_processing
                    SET final_file_path = ?
                    WHERE id = ?
                        AND status = ?
                        AND final_file_path IS NULL
                """;

        var updateRows = jdbcTemplate.update(sql, finalFilePath, id, status.name());

        return updateRows == 1;
    }

    public Optional<FileProcessing> findByFilePath(String filePath) {
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

        return jdbcTemplate.query(sql, this::mapRow, filePath)
                .stream()
                .findFirst();
    }

}
