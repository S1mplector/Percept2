package com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UnknownExpressionWarningFactory implements WarningFactory {

    private static final Map<String, UnknownExpressionWarningFactory> instances = new ConcurrentHashMap<>();

    private final String expression;
    private final String source;
    private boolean alreadyWarned = false;

    private UnknownExpressionWarningFactory(String expression, String source) {
        this.expression = expression;
        this.source = source;
    }

    public static UnknownExpressionWarningFactory getInstance(String expression, String source) {
        String key = expression + "::" + source;
        return instances.computeIfAbsent(key, k -> new UnknownExpressionWarningFactory(expression, source));
    }

    @Override
    public Warning createWarning() {
        if (alreadyWarned) {
            return null;
        }
        alreadyWarned = true;
        String message = "Unknown expression encountered: \"" + expression + "\"";
        return new Warning(message, source);
    }
}