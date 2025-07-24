package org.monitor.model;

import java.nio.file.attribute.FileTime;

public record FileInfo(
        String fileName,
        FileTime fileTime
){}
