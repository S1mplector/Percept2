package com.jvn.plugin.api.script;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Immutable script-command call.
 *
 * @param language lower-case source language identifier, currently {@code vns}
 * @param command registered extension ID
 * @param arguments tokenized arguments with quoting already removed
 * @param variables read-only-by-contract snapshot/view of current script variables
 * @param projectDirectory active project, or {@code null} for classpath-only execution
 */
public record ScriptCommandInvocation(
    String language,
    String command,
    List<String> arguments,
    Map<String, Object> variables,
    Path projectDirectory
) {
  /** Normalizes optional values and creates immutable collections. */
  public ScriptCommandInvocation {
    language = language == null ? "" : language;
    command = command == null ? "" : command;
    arguments = arguments == null ? List.of() : List.copyOf(arguments);
    variables = variables == null ? Map.of() : variables;
  }
}
