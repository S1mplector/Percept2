package com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies;

import com.jvn.core.diagnostics.runtime_logs.logging_strategies.LoggingStrategy;
import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

import java.util.ArrayList;
import java.util.List;

public class WarningLoggingStrategy implements WarningStrategy {

    private final List<LoggingStrategy> loggingStrategies;

    public WarningLoggingStrategy() {
        this.loggingStrategies = new ArrayList<>();
    }

    public void addLoggingStrategy(LoggingStrategy loggingStrategy) {
        loggingStrategies.add(loggingStrategy);
    }

    @Override
    public void execute(Warning warning) {
        if (loggingStrategies.isEmpty()) {
            System.err.println("[WarningLoggingStrategy] No logging strategies configured.");
            return;
        }
        for (LoggingStrategy strategy : loggingStrategies) {
            strategy.log(warning);
        }
    }
}
