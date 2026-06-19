package com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

public class UnknownExpressionWarningFactory implements WarningFactory {

    private static final WarningInstanceCache<UnknownExpressionWarningFactory> instances = new WarningInstanceCache<>();

    private final String characterId;
    private final String expression;
    private final String source;
    private final int line;
    private final String rawLine;
    private boolean alreadyWarned = false;

    private UnknownExpressionWarningFactory(String characterId, String expression, String source, int line, String rawLine) {
        this.characterId = characterId;
        this.expression = expression;
        this.source = source;
        this.line = line;
        this.rawLine = rawLine;
    }

    public static UnknownExpressionWarningFactory getInstance(String characterId, String expression, String source, int line, String rawLine) {
        String key = characterId + "::" + expression + "::" + source + "::" + line;
        return instances.getOrCreate(key, () -> new UnknownExpressionWarningFactory(characterId, expression, source, line, rawLine));
    }

    @Override
    public Warning createWarning() {
        if (alreadyWarned) {
            return null;
        }
        alreadyWarned = true;
        String message = "Unknown expression \"" + expression + "\" for character \"" + characterId
                + "\" at line " + line + ": " + rawLine;
        return new Warning(message, source);
    }
}