package com.jvn.plugin.api.script;

import java.util.Map;

/**
 * Result of a plugin script command.
 *
 * @param handled whether this handler accepted the invocation
 * @param value optional adapter-specific return value; VNS currently ignores it
 * @param variables variable assignments applied to script state after successful execution
 */
public record ScriptCommandResult(boolean handled, Object value, Map<String, Object> variables) {
  /** Normalizes variable updates to an immutable map. */
  public ScriptCommandResult {
    variables = variables == null ? Map.of() : Map.copyOf(variables);
  }

  /** Creates a handled result.
   * @param value optional value
   * @return result without updates
   */
  public static ScriptCommandResult handled(Object value) {
    return new ScriptCommandResult(true, value, Map.of());
  }

  /** Creates an unhandled result.
   * @return result without value or updates
   */
  public static ScriptCommandResult unhandled() {
    return new ScriptCommandResult(false, null, Map.of());
  }
}
