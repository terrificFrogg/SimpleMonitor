package org.monitor.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monitor.model.EventType;
import org.monitor.model.MonitorListener;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileSystemMonitor {
    private static final Logger logger = LogManager.getLogger();

    private final List<MonitorListener> monitorListeners;
    private final Path monitoredPath;
    private WatchService watchService;

    public FileSystemMonitor(FileSystemMonitor.Builder builder) {
        monitorListeners = builder.monitorListeners;
        this.monitoredPath = builder.monitoredPath;

        if(!Files.exists(monitoredPath) || !Files.isDirectory(monitoredPath)){
            logger.error("'{}' must exist and be a directory", monitoredPath);
            throw new IllegalArgumentException("Path must exist and be a directory.");
        }
    }

    public void registerWatchService(){
        try{
            this.watchService = FileSystems.getDefault().newWatchService();
            this.monitoredPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
        } catch (IOException e) {
            logger.error("[FileSystemMonitor] Register Watch Service: {}", e.getMessage());
        }
    }

    public static class Builder{
        private List<MonitorListener> monitorListeners;
        private Path monitoredPath;

        public Builder withMonitoredPath(Path monitoredPath){
            this.monitoredPath = monitoredPath;
            return this;
        }

        public Builder withMonitoredListeners(List<MonitorListener> monitorListeners){
            this.monitorListeners = monitorListeners;
            return this;
        }

        public FileSystemMonitor build() throws IOException {
            if(monitorListeners == null){
                this.monitorListeners = new ArrayList<>();
            }

            if(monitoredPath == null){
                throw new IllegalArgumentException("Monitored path cannot be null");
            }

            return new FileSystemMonitor(this);
        }
    }

    public void addListener(MonitorListener monitorListener){
        this.monitorListeners.add(monitorListener);
    }

    /**
     * Starts the continuous monitoring of the directory.
     * Processes only file creation events and schedules their movement.
     */
    public void startMonitoring() {
        logger.info("Monitoring '{}' for new entries", monitoredPath);
        WatchKey key;
        while (true) {
            try {
                // Retrieve the next queued watch key, waiting indefinitely
                key = watchService.take();
            } catch (InterruptedException x) {
                //logger.error("Folder monitoring interrupted. Exiting.");
                Thread.currentThread().interrupt(); // Restore the interrupted status
                return;
            }

            // Iterate over all events for the retrieved key
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // Handle overflow event (some events might have been lost)
                if (kind == OVERFLOW) {
                    logger.error("Event overflow occurred. Some events might have been lost.");
                    continue;
                }

                // We are only interested in ENTRY_CREATE events
                if (kind == ENTRY_CREATE) {
                    Path createdFilePath = monitoredPath.resolve((Path) event.context()); // The name of the created file/directory
                    // Check if it's a regular file (not a directory)
                    // This is important because WatchService also fires events for directory creation
                    if (Files.isRegularFile(createdFilePath)) {
                        try {
                            waitForFile(createdFilePath);
                            notify(createdFilePath, EventType.FILE);
                        } catch (IOException | InterruptedException e) {
                            logger.error(e);
                            throw new RuntimeException(e);
                        }
                    } else if (Files.isDirectory(createdFilePath)) {
                        notify(createdFilePath, EventType.FOLDER);
                    }
                }
            }

            // Reset the key. If the key is no longer valid, the loop will exit.
            if (!key.reset()) {
                logger.info("Watch key no longer valid. Monitored directory might have been deleted or unaccessible. Exiting monitoring.");
                close();
                monitorListeners.forEach(MonitorListener::onMonitorFailed);
            }
        }
    }

    private void notify(Path detectedPath, EventType eventType){
        monitorListeners.forEach(monitorListener -> monitorListener.onDetected(detectedPath, eventType));
    }

    /**
     * Waits until a file stops changing size (i.e., finished being written).
     */
    private void waitForFile(Path file) throws IOException, InterruptedException {
        long previousSize = -1L;
        long currentSize = Files.size(file);

        // Keep looping until size stabilizes
        while (previousSize != currentSize) {
            previousSize = currentSize;
            Thread.sleep(500); // half a second delay
            currentSize = Files.size(file);
        }
    }

    public void close(){
        try {
            watchService.close();
        } catch (IOException e) {
            logger.error("[FileSystemMonitor] Error when closing WatchService: {}", e.getMessage());
        }
    }
}
