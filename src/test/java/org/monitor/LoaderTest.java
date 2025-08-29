package org.monitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.monitor.model.ConfigCollection;
import org.monitor.service.ConfigParser;

public class LoaderTest {

    @Test
    void configLoadTest(){
        Assertions.assertDoesNotThrow(() -> {
            ConfigParser.parseConfig("Config.json");
        });
    }
}
