package br.com.jards.filepulse.watcher.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.jards.filepulse.watcher.enums.MotivoRejeicaoArquivo;

@Repository
public class RejeicaoArquivoRepository {

    private final JdbcTemplate jdbcTemplate;

    public RejeicaoArquivoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void criar(String nomeArquivo, String caminhoArquivo, String hashArquivo, MotivoRejeicaoArquivo motivo,
            String detalhes) {
        String sql = """
                    INSERT INTO file_rejection (file_name, file_path, file_hash, reason, details)
                    VALUES (?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                nomeArquivo,
                caminhoArquivo,
                hashArquivo,
                motivo.name(),
                detalhes);
    }

}
