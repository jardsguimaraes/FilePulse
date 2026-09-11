package br.com.jards.filepulse.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;

@Component
public class DirectoryWatcher {

    private final FileStorageProperties properties;
    private final FileEventHandler handler;

    public DirectoryWatcher(FileStorageProperties properties, FileEventHandler handler) {
        this.properties = properties;
        this.handler = handler;
    }

    @PostConstruct
    public void start() throws IOException {
        var directory = Path.of(properties.getInputDirectory());

        var watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        Thread.startVirtualThread(() -> watch(directory, watchService));
    }

    private void watch(Path directory, WatchService watchService) {
        while (true) {
            try {
                var key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    var relativePath = (Path) event.context();
                    var file = directory.resolve(relativePath);

                    Thread.startVirtualThread(() -> handler.onFileCreated(file));
                }

                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
