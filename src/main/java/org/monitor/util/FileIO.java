package org.monitor.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileIO {
    private static final Logger logger = LogManager.getLogger();

    public static String readFileAsString(String filePath){
        StringBuilder stringBuilder = new StringBuilder();
        if(filePath != null && !filePath.isBlank()){
            Path path = Path.of(filePath);
            try (BufferedReader reader = Files.newBufferedReader(path)){
                BOMSkipper.skip(reader);
                String line;
                while((line = reader.readLine()) != null){
                    stringBuilder.append(line);
                }
            } catch (IOException e) {
                logger.error("Error when trying to read ".concat(filePath), e);
                throw new RuntimeException(e);
            }
        }
        return stringBuilder.toString();
    }

    public static Reader readFileAsReader(String filePath){
        if(filePath != null && !filePath.isBlank()){
            try {
                Reader reader = Files.newBufferedReader(Path.of(filePath));
                BOMSkipper.skip(reader);
                return reader;
            } catch (IOException e) {
                logger.error("Error when trying to read ".concat(filePath), e);
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static void saveFile(String fileName, String content){
        try(BufferedWriter writer = Files.newBufferedWriter(Path.of(fileName))) {
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            logger.error("Error when trying to write to ".concat(fileName), e);
            throw new RuntimeException(e);
        }
    }

    private static class BOMSkipper {
        /**
         * Skips the Byte Order Mark in UTF-8 encoded files
         */
        public static void skip(Reader reader) throws IOException {
            reader.mark(1);
            char[] possibleBOMBuffer = new char[1];
            reader.read(possibleBOMBuffer);

            if (possibleBOMBuffer[0] != '\ufeff') {
                reader.reset();
            }
        }
    }
}
