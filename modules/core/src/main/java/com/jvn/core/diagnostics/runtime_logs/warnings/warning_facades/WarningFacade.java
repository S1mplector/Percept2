package com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories.WarningFactory;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies.WarningStrategy;

import java.util.List;

/**
 * Manager of the warning logging process
 */
public interface WarningFacade {

    void setFactory(WarningFactory factory);

    void setStrategy(WarningStrategy strategy);

    void triggerWarning();

    List<Warning> getWarnings();
}