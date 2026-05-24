package com.jvn.core.diagnostics.runtime_logs.warnings.warning_factories;

import com.jvn.core.diagnostics.runtime_logs.warnings.Warning;

public interface WarningFactory {
    Warning createWarning();
}