package br.com.jards.filepulse.watcher;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;

import org.springframework.stereotype.Component;

import br.com.jards.filepulse.watcher.config.ArmazenamentoArquivoProperties;
import jakarta.annotation.PostConstruct;

@Component
public class MonitorDiretorio {

    private final ArmazenamentoArquivoProperties propriedadesArmazenamento;
    private final ManipuladorEventoArquivo manipuladorEventoArquivo;

    public MonitorDiretorio(ArmazenamentoArquivoProperties propriedadesArmazenamento,
            ManipuladorEventoArquivo manipuladorEventoArquivo) {
        this.propriedadesArmazenamento = propriedadesArmazenamento;
        this.manipuladorEventoArquivo = manipuladorEventoArquivo;
    }

    @PostConstruct
    public void iniciar() throws IOException {
        var directory = Path.of(propriedadesArmazenamento.getDiretorioEntrada());

        var watchService = FileSystems.getDefault().newWatchService();
        directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

        Thread.startVirtualThread(() -> monitorar(directory, watchService));
    }

    private void monitorar(Path diretorio, WatchService watchService) {
        while (true) {
            try {
                var chave = watchService.take();

                for (WatchEvent<?> event : chave.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    var caminhoRelativo = (Path) event.context();
                    var arquivo = diretorio.resolve(caminhoRelativo);

                    Thread.startVirtualThread(() -> manipuladorEventoArquivo.aoCriarArquivo(arquivo));
                }

                boolean valida = chave.reset();
                if (!valida) {
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
