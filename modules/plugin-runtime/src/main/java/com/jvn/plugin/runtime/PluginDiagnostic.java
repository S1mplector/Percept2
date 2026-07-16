package com.jvn.plugin.runtime;

public record PluginDiagnostic(Severity severity, String pluginId, String code, String message, Throwable cause) {
  public enum Severity { INFO, WARNING, ERROR }

  public PluginDiagnostic {
    pluginId = pluginId == null ? "" : pluginId;
    code = code == null ? "" : code;
    message = message == null ? "" : message;
  }
}
