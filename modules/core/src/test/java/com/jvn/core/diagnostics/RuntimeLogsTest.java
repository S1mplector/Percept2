package com.jvn.core.diagnostics;

import com.jvn.core.diagnostics.runtime_logs.logging_strategies.LogCLI;
import com.jvn.core.diagnostics.runtime_logs.warnings.WarningSubscriber;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades.WarningFacade;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_facades.WarningManager;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories.UnknownExpressionWarningFactory;
import com.jvn.core.diagnostics.runtime_logs.warnings.warning_strategies.WarningLoggingStrategy;
import org.junit.jupiter.api.Test;

public class RuntimeLogsTest {

    /**
     * Super general test, ignore, not a real test
     */
    @Test
    public void testRuntimeLogs() {
        LogCLI cli = new LogCLI();

        WarningLoggingStrategy strategy = new WarningLoggingStrategy();
        strategy.addLoggingStrategy(cli);

        WarningFacade warningManager = new WarningManager();
        warningManager.setStrategy(strategy);

        WarningSubscriber subscriber = new WarningSubscriber(warningManager);

        subscriber.onWarningEvent(UnknownExpressionWarningFactory.getInstance(
                "TEST_CHARACTER", "INVALID", "Unit Test", 12, "show TEST_CHARACTER pos INVALID"));
    }
}