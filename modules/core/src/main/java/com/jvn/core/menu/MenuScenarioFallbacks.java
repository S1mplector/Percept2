package com.jvn.core.menu;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;

final class MenuScenarioFallbacks {
  private MenuScenarioFallbacks() {
  }

  static VnScenario missingScriptScenario(String scriptName, Exception cause) {
    String script = scriptName == null || scriptName.isBlank() ? "<unspecified>" : scriptName.trim();
    String detail = cause == null
        ? "unknown error"
        : (cause.getMessage() == null || cause.getMessage().isBlank() ? cause.toString() : cause.getMessage());
    return new VnScenarioBuilder("missing_script")
        .label("start")
        .dialogue("System", "Unable to load script: " + script)
        .dialogue("System", "Details: " + detail)
        .end()
        .build();
  }
}
