package com.jvn.core.diagnostics.runtime_logs.warnings;

import java.time.LocalDateTime;

public class Warning {

    private final String message;
    private final String source;
    private final LocalDateTime timestamp;

    public Warning(String message, String source) {
        this.message = message;
        this.source = source;
        this.timestamp = LocalDateTime.now();
    }

    public String getMessage() {
        return message;
    }

    public String getSource() {
        return source;
    }


    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return timestamp + " | Source: " + source + " | Message: " + message;
    }
}