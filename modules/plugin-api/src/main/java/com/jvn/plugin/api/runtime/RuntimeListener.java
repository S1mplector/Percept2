package com.jvn.plugin.api.runtime;

/**
 * Runtime lifecycle observer. Callbacks execute synchronously during host transitions; they should
 * remain short. Failures are isolated, recorded as warnings, and do not prevent other listeners.
 */
public interface RuntimeListener {
  /** Called after startup.
   * @param event context
   * @throws Exception to report failure
   */
  default void onRuntimeStarted(RuntimeEvent event) throws Exception {}
  /** Called when a project is available.
   * @param event context
   * @throws Exception to report failure
   */
  default void onProjectOpened(RuntimeEvent event) throws Exception {}
  /** Called before shutdown.
   * @param event context
   * @throws Exception to report failure
   */
  default void onRuntimeStopping(RuntimeEvent event) throws Exception {}
}
