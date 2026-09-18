package br.com.jards.filepulse.watcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "fileprocessor.directory")
public class FileStorageProperties {

    private String inputDirectory;
    private String processingDirectory;
    private String processedDirectory;
    private String errorDirectory;
    private String rejectedDirectory;
}
