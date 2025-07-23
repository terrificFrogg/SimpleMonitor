package org.monitor.model;

import java.util.concurrent.TimeUnit;

public record Config(String sourceFolder, String archiveFolder, Action action, int delay, TimeUnit timeUnit) {}
