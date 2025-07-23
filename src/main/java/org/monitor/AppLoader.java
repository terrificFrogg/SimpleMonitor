package org.monitor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monitor.model.Config;
import org.monitor.model.ConfigCollection;
import org.monitor.service.ConfigParser;
import org.monitor.service.FolderMonitor;
import org.monitor.util.FileIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLoader {
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        logger.info("App Started");
        String configFileName = "Config.json";
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("App Closing");
        }));

        if(Files.exists(Path.of(configFileName))){
            ConfigCollection configCollection = ConfigParser.parseConfig(configFileName);
            if(configCollection != null && configCollection.configs() != null){
                try(ExecutorService es = Executors.newFixedThreadPool(configCollection.configs().size())){
                    List<Callable<Void>> callableList = new ArrayList<>();
                    for(Config config : configCollection.configs()){
                        if(config != null){
                            callableList.add(() -> {
                                try {
                                    FolderMonitor folderMonitor = new FolderMonitor(config);
                                    folderMonitor.startMonitoring();
                                } catch (IOException e) {
                                    logger.error("Error: {}", e.getMessage());
                                }
                                return null;
                            });
                        }else{
                            logger.error("Config file was found however config object is null.");
                        }
                    }
                    es.invokeAll(callableList);
                } catch (InterruptedException e) {
                    logger.error("Execution thread interrupted: {}", e.getMessage());
                    throw new RuntimeException(e);
                }
            }

        }else{
            logger.error("{} file not found in current directory. Ensure file is created and named as {}\n A " +
                    "default config file has been created. " +
                    "Change the paths and other values accordingly.", configFileName, configFileName);
            String defaultConfig = "{\n" +
                    "\t\"configs\": [\n" +
                    "\t\t{\n" +
                    "\t\t\t\"sourceFolder\": \"C:\\\\Dev\\\\FolderMonitorTesting\\\\Source 1\",\n" +
                    "\t\t\t\"archiveFolder\": \"C:\\\\Dev\\\\FolderMonitorTesting\\\\ArchiveFolder\",\n" +
                    "\t\t\t\"action\": \"MOVE\",\n" +
                    "\t\t\t\"delay\": 5,\n" +
                    "\t\t\t\"timeUnit\": \"SECONDS\"\n" +
                    "\t\t},\n" +
                    "\t\t{\n" +
                    "\t\t\t\"sourceFolder\": \"C:\\\\Dev\\\\FolderMonitorTesting\\\\Source 2\",\n" +
                    "\t\t\t\"archiveFolder\": \"C:\\\\Dev\\\\FolderMonitorTesting\\\\ArchiveFolder\",\n" +
                    "\t\t\t\"action\": \"MOVE\",\n" +
                    "\t\t\t\"delay\": 5,\n" +
                    "\t\t\t\"timeUnit\": \"SECONDS\"\n" +
                    "\t\t}\n" +
                    "\t]\n" +
                    "}";
            FileIO.saveFile(configFileName, defaultConfig);
        }
    }
}