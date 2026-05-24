package com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

public class UnknownExpressionWarningFactory implements WarningFactory {

    private final String expression;
    private final String source;

    public UnknownExpressionWarningFactory(String expression, String source) {
        this.expression = expression;
        this.source = source;
    }

    @Override
    public Warning createWarning() {
        String message = "Unknown expression encountered: \"" + expression + "\"";
        return new Warning(message, source);
    }
}
