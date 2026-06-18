package com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

public class UnknownExpressionWarningFactory implements WarningFactory {

    private static final WarningInstanceCache<UnknownExpressionWarningFactory> instances = new WarningInstanceCache<>();

    private final String characterId;
    private final String expression;
    private final String source;
    private boolean alreadyWarned = false;

    private UnknownExpressionWarningFactory(String characterId, String expression, String source) {
        this.characterId = characterId;
        this.expression = expression;
        this.source = source;
    }

    public static UnknownExpressionWarningFactory getInstance(String characterId, String expression, String source) {
        String key = characterId + "::" + expression + "::" + source;
        return instances.getOrCreate(key, () -> new UnknownExpressionWarningFactory(characterId, expression, source));
    }

    @Override
    public Warning createWarning() {
        if (alreadyWarned) {
            return null;
        }
        alreadyWarned = true;
        String message = "Unknown expression \"" + expression + "\" for character \"" + characterId + "\"";
        return new Warning(message, source);
    }
}