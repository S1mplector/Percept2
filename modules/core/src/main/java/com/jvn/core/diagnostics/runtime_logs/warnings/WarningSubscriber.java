package com.jvn.core.diagnostics.runtime_logs.warnings;

import com.jvn.core.diagnostics.runtime_logs.logging_strategies.LogCLI;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades.WarningFacade;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories.WarningFactory;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies.WarningLoggingStrategy;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies.WarningStrategy;

public class WarningSubscriber {

    private final WarningFacade warningFacade;

    public WarningSubscriber(WarningFacade warningFacade) {
        this.warningFacade = warningFacade;
    }

    public void onWarningEvent(WarningFactory factory) {
        warningFacade.setFactory(factory);
        WarningStrategy warningStrategy = new WarningLoggingStrategy();
        warningStrategy.addLoggingStrategy(new LogCLI());
        warningFacade.setStrategy(warningStrategy);
        warningFacade.triggerWarning();
    }
}
