package com.jvn.core.diagnostics.runtime_logs.warnings;

import com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades.WarningFacade;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories.WarningFactory;

public class WarningSubscriber {

    private final WarningFacade warningFacade;

    public WarningSubscriber(WarningFacade warningFacade) {
        this.warningFacade = warningFacade;
    }

    public void onWarningEvent(WarningFactory factory) {
        warningFacade.setFactory(factory);
        warningFacade.triggerWarning();
    }
}
