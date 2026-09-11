package br.com.jards.filepulse.watcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "fileprocessor.directory")
@Getter
@Setter
public class FileWatcherProperties {

    private String inputDirectory;
}
