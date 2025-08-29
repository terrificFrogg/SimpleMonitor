package org.monitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.monitor.model.Action;
import org.monitor.model.Config;
import org.monitor.service.FolderMonitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MonitorTest {
    private Config config;

    void init(){
        if(config != null)
            return;

        config = new Config(
                "FolderMonitorTest/Source",
                "FolderMonitorTest/Destination",
                Action.COPY,
                1,
                TimeUnit.SECONDS
        );
    }


    @Test
    void testFolderMonitorDoesNotThrow(){
        init();
        Assertions.assertDoesNotThrow(() -> {
            FolderMonitor folderMonitor = new FolderMonitor(config);
        });
    }

    @Test
    void testFileIsCreatedInDestination(){
        init();
        try {
            FolderMonitor folderMonitor = new FolderMonitor(config);

            try(ExecutorService es = Executors.newSingleThreadExecutor()){
                es.submit(folderMonitor::startMonitoring);

                Path testFile = Path.of("FolderMonitorTest/Test.txt");
                Path destinationFolder = Path.of("FolderMonitorTest/Destination");

                Files.copy(testFile, Path.of("FolderMonitorTest/Source").resolve(testFile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                Thread.sleep(Duration.of(config.delay() + 1, config.timeUnit().toChronoUnit()));
                folderMonitor.shutdown();
                es.shutdownNow();
                Assertions.assertTrue(Files.exists(destinationFolder.resolve(testFile.getFileName())));
            }

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
