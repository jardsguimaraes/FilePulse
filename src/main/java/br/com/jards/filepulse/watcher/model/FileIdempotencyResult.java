package br.com.jards.filepulse.watcher.model;

public record FileIdempotencyResult(
        String fileHash,
        boolean duplicate) {

}
