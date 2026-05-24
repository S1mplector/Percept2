package com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

/**
 * Follows the Strategy design pattern.
 * Different strategies for different processes
 * that the program might want to perform when a warning is created
 */
public interface WarningStrategy {
    void execute(Warning warning);
}