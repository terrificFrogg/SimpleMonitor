package org.monitor.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monitor.model.Action;
import org.monitor.model.Config;
import org.monitor.util.FileHasher;

import java.io.IOException;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FolderMonitor {
    private static final Logger logger = LogManager.getLogger();

    private final WatchService watcher;
    private final Path monitoredDir;
    private final Path archiveDir;
    private final ScheduledExecutorService scheduler;
    private final int delayMinutes; // Delay before moving the file
    private final TimeUnit timeUnit;
    private final Action action;
    private static final String ALGORITHM = "MD5";

    /**
     * Creates a WatchService and registers the given directory.
     * Initializes the archive directory and a scheduler for delayed tasks.
     *
     * @param config A config object Config.java
     * @throws IOException If an I/O error occurs during setup.
     */
    public FolderMonitor(Config config) throws IOException {
        this.delayMinutes = config.delay();
        this.timeUnit = config.timeUnit();
        this.action = config.action();
        this.watcher = FileSystems.getDefault().newWatchService();
        this.monitoredDir = Paths.get(config.sourceFolder());
        this.archiveDir = Paths.get(config.archiveFolder());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(); // Single thread for scheduled tasks

        // Validate monitored directory
        if (!Files.exists(monitoredDir) || !Files.isDirectory(monitoredDir)) {
            logger.error("Monitored path is not a valid directory: {}", monitoredDir);
            throw new IllegalArgumentException("Monitored path is not a valid directory: " + monitoredDir);
        }

        // Ensure archive directory exists, create if not
        if (!Files.exists(archiveDir)) {
            try {
                Files.createDirectories(archiveDir);
                logger.info("Created archive directory: {}", archiveDir.toAbsolutePath());
            } catch (IOException e) {
                logger.error("Failed to create archive directory: {}", archiveDir, e);
                throw new IOException("Failed to create archive directory: " + archiveDir, e);
            }
        } else if (!Files.isDirectory(archiveDir)) {
            logger.error("Archive path exists but is not a directory: {}", archiveDir);
            throw new IllegalArgumentException("Archive path exists but is not a directory: " + archiveDir);
        }

        // Register the monitored directory for ENTRY_CREATE events only
        this.monitoredDir.register(watcher, ENTRY_CREATE);

        logger.info("Monitoring directory for new files: '{}'", monitoredDir.toAbsolutePath());
        logger.info("Files will be archived from '{}' to '{}' after {} {}.", monitoredDir.toAbsolutePath(),
                archiveDir.toAbsolutePath(), delayMinutes, timeUnit.toString().toLowerCase());

        // Add a shutdown hook to gracefully terminate the scheduler when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /**
     * Starts the continuous monitoring of the directory.
     * Processes only file creation events and schedules their movement.
     */
    public void startMonitoring() {
        while (true) {
            WatchKey key;
            try {
                // Retrieve the next queued watch key, waiting indefinitely
                key = watcher.take();
            } catch (InterruptedException x) {
                logger.error("Folder monitoring interrupted. Exiting.");
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
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context(); // The name of the created file/directory

                    // Resolve the full path of the newly created file/directory
                    Path createdFilePath = monitoredDir.resolve(filename);

                    // Check if it's a regular file (not a directory)
                    // This is important because WatchService also fires events for directory creation
                    if (Files.isRegularFile(createdFilePath)) {
                        logger.info("[CREATED] Detected new file: {}", createdFilePath.toAbsolutePath());
                        Optional<String> optional = FileHasher.hashFile(createdFilePath.toFile(), ALGORITHM);
                        optional.ifPresent(fileHash -> {
                            scheduleFileMove(createdFilePath, fileHash);
                        });
                    } else if (Files.isDirectory(createdFilePath)) {
                        logger.info("[CREATED] Detected new directory (will not {}): {}", action.toString(), createdFilePath.toAbsolutePath());
                    }
                }
            }

            // Reset the key. If the key is no longer valid, the loop will exit.
            boolean valid = key.reset();
            if (!valid) {
                logger.info("Watch key no longer valid. Monitored directory might have been deleted or unaccessible. Exiting monitoring.");
                break;
            }
        }
    }

    public void shutdown(){
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                logger.error("Scheduler did not terminate in time, forced shutdown.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            logger.error("Scheduler shutdown interrupted.");
        }
    }

    /**
     * Schedules the movement of a file to the archive directory after a specified delay.
     *
     * @param filePath The path of the file to be moved.
     */
    private void scheduleFileMove(Path filePath, String fileHash) {
        switch (action){
            case MOVE, COPY -> {
                logger.info("Scheduling {} for '{}' to archive in {} {}.", action.toString(), filePath.getFileName(), delayMinutes, timeUnit.toString().toLowerCase());
            }

            case DELETE -> {
                logger.info("Scheduling {} for '{}' in {} {}.", action.toString(), filePath.getFileName(), delayMinutes, timeUnit.toString().toLowerCase());
            }
        }

        scheduler.schedule(() -> {
            try {
                // Construct the destination path in the archive directory
                Path destinationPath = archiveDir.resolve(filePath.getFileName());

                /*
                 Check if the file still exists in the source directory before moving.
                 Files.exists might return true even though the file contents are now different.
                 Checking against file hash will confirm we're still operating on the original file.
                */
                Optional<String> optional = FileHasher.hashFile(filePath.toFile(), ALGORITHM);
                if (Files.exists(filePath) && optional.isPresent()) {
                    if(optional.get().equals(fileHash)){
                        switch (action) {
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
                    logger.info("[SKIPPED] File '{}' no longer exists in source, skipping {}.", filePath.getFileName(), action.toString());
                }
            } catch (IOException e) {
                logger.error("[ERROR] Failed to {} '{}' | ", action.toString(), filePath.getFileName(), e);
            } catch (Exception e) {
                logger.error(e);
                throw new RuntimeException(e);
            }
        }, delayMinutes, timeUnit);
    }
}