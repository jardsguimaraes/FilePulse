package br.com.jards.filepulse.watcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "fileprocessor.stability")
public class FileStabilityProperties {

    private int stableChecks;
    private int maxAttempts;
    private long checkIntervalMs;
    private int maxRetries;
    private long retryIntervalMs;
}
