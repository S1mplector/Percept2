package com.jvn.plugin.api.script;

/**
 * Host-neutral command handler usable by VNS and future JES adapters.
 *
 * <p>The runtime invokes handlers synchronously on its script-processing thread. Handlers should
 * return quickly and must not mutate the invocation's variable map directly. Return variable
 * updates through {@link ScriptCommandResult#variables()}.</p>
 */
@FunctionalInterface
public interface ScriptCommand {
  /**
   * Executes one registered command.
   * @param invocation immutable call metadata and argument snapshot
   * @return non-null handling result
   * @throws Exception to report a command failure through runtime interop diagnostics
   */
  ScriptCommandResult execute(ScriptCommandInvocation invocation) throws Exception;
}
