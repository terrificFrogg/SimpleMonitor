package org.monitor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monitor.model.Config;
import org.monitor.model.ConfigCollection;
import org.monitor.util.FileIO;

public final class ConfigParser {
    private static final Logger logger = LogManager.getLogger();

    public static ConfigCollection parseConfig(String filePath){
        logger.info("Parsing config file: {}", filePath);
        try {
            String jsonData = FileIO.readFileAsString(filePath);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonData, ConfigCollection.class);
        } catch (JsonProcessingException e) {
            logger.error(e);
        }
        return null;
    }
}
