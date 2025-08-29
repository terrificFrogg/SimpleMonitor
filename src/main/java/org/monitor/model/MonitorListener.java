package org.monitor.model;

import java.nio.file.Path;

public interface MonitorListener {

    void onDetected(Path detectedPath, EventType eventType);
}
