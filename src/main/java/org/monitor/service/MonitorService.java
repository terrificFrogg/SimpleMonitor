package org.monitor.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monitor.model.Config;
import org.monitor.model.EventType;
import org.monitor.model.MonitorListener;
import org.monitor.util.FileHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MonitorService implements MonitorListener {
    private final Logger logger;
    private final Config config;
    private final FileSystemMonitor fileSystemMonitor;
    private final ScheduledExecutorService scheduledExecutorService;
    private int restartCounter;

    public MonitorService(MonitorService.Builder builder){
        this.config = builder.config;
        this.fileSystemMonitor = builder.fileSystemMonitor;
        this.scheduledExecutorService = builder.scheduledExecutorService;
        this.logger = builder.logger;
        this.fileSystemMonitor.addListener(this);
        restartCounter = 0;
    }

    public static class Builder{
        private Logger logger;
        private Config config;
        private FileSystemMonitor fileSystemMonitor;
        private ScheduledExecutorService scheduledExecutorService;

        public Builder withLogger(Logger logger){
            this.logger = logger;
            return this;
        }

        public Builder withConfig(Config config){
            this.config = config;
            return this;
        }

        public Builder withFileSystemMonitor(FileSystemMonitor fileSystemMonitor){
            this.fileSystemMonitor = fileSystemMonitor;
            return this;
        }

        public Builder withScheduledExecutorService(ScheduledExecutorService scheduledExecutorService){
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        public MonitorService build(){
            if(config == null){
                throw new IllegalArgumentException("Config must not be null");
            }

            if(fileSystemMonitor == null){
                throw new IllegalArgumentException("FileSystemMonitor must not be null");
            }

            if(scheduledExecutorService == null){
                throw new IllegalArgumentException("ScheduledExecutorService must not be null");
            }

            if(logger == null){
                throw new IllegalArgumentException("Logger must not be null");
            }

            return new MonitorService(this);
        }
    }

    public void startMonitor() throws IOException {
        this.fileSystemMonitor.registerWatchService();
        this.fileSystemMonitor.startMonitoring();
    }

    @Override
    public void onDetected(Path detectedPath, EventType eventType) {
        if (Objects.requireNonNull(eventType) == EventType.FILE) {
            Optional<String> optionalFileHash = FileHasher.hashFile(detectedPath.toFile(), FileHasher.ALGO_MD5);
            if(optionalFileHash.isPresent()){
                schedule(detectedPath, optionalFileHash.get());
            }else{
                logger.error("No hash for {}", detectedPath.getFileName());
            }
        }
    }

    @Override
    public void onMonitorFailed() {
        logger.info("Restarting monitoring");
        this.fileSystemMonitor.registerWatchService();
        this.fileSystemMonitor.startMonitoring();
        restartCounter++;
        if(restartCounter == 2){
            shutdown();
        }
    }

    private void schedule(Path filePath, String fileHash){
        switch (config.action()){
            case MOVE, COPY -> {
                logger.info("Scheduling {} for '{}' to archive in {} {}.", config.action().toString(), filePath.getFileName(), config.delay(), config.timeUnit().toString().toLowerCase());
            }

            case DELETE -> {
                logger.info("Scheduling {} for '{}' in {} {}.", config.action().toString(), filePath.getFileName(), config.delay(), config.timeUnit().toString().toLowerCase());
            }
        }

        scheduledExecutorService.schedule(() -> {
            try {
                // Construct the destination path in the archive directory
                Path destinationPath = Path.of(config.archiveFolder()).resolve(filePath.getFileName());

                /*
                 Check if the file still exists in the source directory before moving.
                 Files.exists might return true even though the file contents are now different.
                 Checking against file hash will confirm we're still operating on the original file.
                */
                Optional<String> optional = FileHasher.hashFile(filePath.toFile(), FileHasher.ALGO_MD5);
                if (Files.exists(filePath) && optional.isPresent()) {
                    if(optional.get().equals(fileHash)){
                        switch (config.action()) {
                            case MOVE -> {
                                Files.move(filePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                                logger.info("[MOVED] Successfully archived '{}' to '{}'", filePath.getFileName(), destinationPath.toAbsolutePath());
                            }

                            case COPY -> {
                                Files.copy(filePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
                                logger.info("[COPY] Successfully archived '{}' to '{}'", filePath.getFileName(), destinationPath.toAbsolutePath());
                            }

                            case DELETE -> {
                                Files.deleteIfExists(filePath);
                                logger.info("[DELETE] Successfully deleted '{}'", filePath.getFileName());
                            }
                        }
                    }else{
                        logger.info("File Hash for '{}' doesn't match original. Skipping file.", filePath.getFileName());
                    }
                } else {
                    logger.info("[SKIPPED] File '{}' no longer exists in source, skipping {}.",
                            filePath.getFileName(), config.action().toString());
                }
            } catch (IOException e) {
                logger.error("Failed to {} '{}' | ", config.action().toString(), filePath.getFileName(), e);
            } catch (Exception e) {
                logger.error(e);
                throw new RuntimeException(e);
            }
        }, config.delay(), config.timeUnit());
    }

    public void shutdown(){
        fileSystemMonitor.close();
        scheduledExecutorService.shutdown();
        try {
            if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
                logger.error("Scheduler did not terminate in time, forced shutdown.");
            }
        } catch (InterruptedException e) {
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
            logger.error("Scheduler shutdown interrupted.");
        }
    }
}
