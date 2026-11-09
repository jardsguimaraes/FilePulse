package br.com.jards.filepulse.batch.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

@Service
public class HashArquivoService {

    public String calcular(Path arquivo) {
        try (InputStream fluxoEntrada = Files.newInputStream(arquivo)) {

            MessageDigest resumoCriptografico = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[8192];
            int bytesLidos;

            while ((bytesLidos = fluxoEntrada.read(buffer)) != -1) {
                resumoCriptografico.update(buffer, 0, bytesLidos);
            }

            return HexFormat.of()
                    .formatHex(resumoCriptografico.digest());

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Não foi possível calcular o hash do arquivo: %s"
                            .formatted(arquivo),
                    e);
        }
    }
}
