package br.com.jards.filepulse.watcher.repositeries;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.watcher.enums.FileRejectionReason;

@Repository
public class FileRejectionRepository {

    private final JdbcTemplate jdbcTemplate;

    public FileRejectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(String fileName, String filePath, String fileHash, FileRejectionReason reason, String details) {
        String sql = """
                    INSERT INTO file_rejection (file_name, file_path, file_hash, reason, details)
                    VALUES (?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                fileName,
                filePath,
                fileHash,
                reason.name(),
                details);
    }

}
