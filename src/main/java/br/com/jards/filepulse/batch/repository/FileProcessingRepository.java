package br.com.jards.filepulse.batch.repository;

import java.sql.PreparedStatement;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.batch.enums.FileProcessingStatus;

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
            String errorMessage) {
        String sql = """
                UPDATE file_processing
                SET status = ?,
                    read_count = ?,
                    write_count = ?,
                    skip_count = ?,
                    finished_at = CURRENT_TIMESTAMP,
                    error_message = ?
                WHERE id = ?
                """;

        jdbcTemplate.update(sql, status.name(), readCount, writeCount, skipCount, errorMessage, id);
    }
}
