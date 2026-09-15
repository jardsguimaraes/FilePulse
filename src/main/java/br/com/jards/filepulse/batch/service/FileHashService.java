package br.com.jards.filepulse.batch.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

@Service
public class FileHashService {

    public String calculate(Path file) {
        try (InputStream inputStream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível calcular o hash do arquivo: %s".formatted(file), e);
        }
    }
}
