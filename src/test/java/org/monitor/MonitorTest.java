package org.monitor;

import com.fasterxml.jackson.core.JsonToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.monitor.model.Action;
import org.monitor.model.Config;
import org.monitor.model.EventType;
import org.monitor.model.MonitorListener;
import org.monitor.service.FileSystemMonitor;
import org.monitor.service.FolderMonitor;
import org.monitor.service.MonitorService;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MonitorTest {
    private static final Logger log = LogManager.getLogger(MonitorTest.class);
    private Config config;
    private Path testSourcePath;
    private Path testDestinationPath;
    private Path testRootFolder;
    private String testFileName;

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

        testSourcePath = Path.of("FolderMonitorTest/Source");
        testDestinationPath = Path.of("FolderMonitorTest/Destination");
        testRootFolder = Path.of("FolderMonitorTest/");
        testFileName = "Test.txt";
    }

    @Test
    void testFileIsCreatedInDestination(){
        init();
        try {
            FileSystemMonitor fileSystemMonitor = new FileSystemMonitor.Builder()
                    .withMonitoredPath(testSourcePath)
                    .withMonitoredListeners(new ArrayList<>())
                    .build();

            try(ExecutorService es = Executors.newSingleThreadExecutor()){
                es.submit(() -> {
                    fileSystemMonitor.registerWatchService();
                    fileSystemMonitor.startMonitoring();
                });
                fileSystemMonitor.addListener(new MonitorListener() {
                    @Override
                    public void onDetected(Path detectedPath, EventType eventType) {
                        LogManager.getLogger().info("Detected: {}", detectedPath.getFileName());

                        if(eventType.equals(EventType.FILE)){
                            Assertions.assertEquals(detectedPath.getFileName().toString(), testFileName);
                            try {
                                Files.delete(testSourcePath.resolve(testFileName));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        es.shutdownNow();
                    }

                    @Override
                    public void onMonitorFailed() {
                        System.err.println("Monitor failed");
                    }
                });
                Path target = testRootFolder.resolve(testFileName);
                Path dest = testSourcePath.resolve(testFileName);
                Files.copy(target, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            //
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
