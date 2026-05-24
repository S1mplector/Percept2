package com.jvn.core.diagnostics.runtime_logs.logging_strategies;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

public class LogCLI implements LoggingStrategy {

    @Override
    public void log(Warning warning) {
        System.out.println("==============================");
        System.out.println("  WARNING");
        System.out.println("==============================");
        System.out.println("  Timestamp : " + warning.getTimestamp());
        System.out.println("  Source    : " + warning.getSource());
        System.out.println("  Message   : " + warning.getMessage());
        System.out.println("==============================");
    }
}