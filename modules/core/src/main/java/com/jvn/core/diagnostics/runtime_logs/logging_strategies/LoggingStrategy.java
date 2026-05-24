package com.jvn.core.diagnostics.runtime_logs.logging_strategies;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

public interface LoggingStrategy {
    void log(Warning warning);
}