package com.jvn.core.diagnostics.runtime_logs.logging_strategies;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

public class LogJSON implements LoggingStrategy {

    private static final String LOGS_DIR = "logs";
    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public void log(Warning warning) {
        try {
            Files.createDirectories(Paths.get(LOGS_DIR));

            String fileName = LOGS_DIR + "/warning_" +
                    warning.getTimestamp().format(FILE_FORMATTER) + ".json";

            String json = buildJson(warning);

            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(json);
            }

            System.out.println("[LogJSON] Warning written to: " + fileName);

        } catch (IOException e) {
            System.err.println("[LogJSON] Failed to write warning log: " + e.getMessage());
        }
    }

    private String buildJson(Warning warning) {
        return "{\n" +
                "  \"timestamp\": \"" + warning.getTimestamp().format(ISO_FORMATTER) + "\",\n" +
                "  \"source\": \"" + escapeJson(warning.getSource()) + "\",\n" +
                "  \"message\": \"" + escapeJson(warning.getMessage()) + "\"\n" +
                "}";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}