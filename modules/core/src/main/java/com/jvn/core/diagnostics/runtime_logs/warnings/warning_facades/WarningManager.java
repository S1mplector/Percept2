package com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories.WarningFactory;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies.WarningStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WarningManager implements WarningFacade {

    private WarningFactory factory;
    private WarningStrategy strategy;
    private final List<Warning> warnings;

    public WarningManager() {
        this.warnings = new ArrayList<>();
    }

    @Override
    public void setFactory(WarningFactory factory) {
        this.factory = factory;
    }

    @Override
    public void setStrategy(WarningStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void triggerWarning() {
        if (factory == null) {
            System.err.println("[WarningManager] No factory set. Cannot create warning.");
            return;
        }
        if (strategy == null) {
            System.err.println("[WarningManager] No strategy set. Cannot handle warning.");
            return;
        }

        Warning warning = factory.createWarning();
        warnings.add(warning);

        strategy.execute(warning);
    }

    @Override
    public List<Warning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
