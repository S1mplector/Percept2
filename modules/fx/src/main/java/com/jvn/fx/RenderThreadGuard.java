package com.jvn.fx;

import javafx.application.Platform;

/**
 * Development-time guard that asserts render operations run on the JavaFX Application Thread.
 *
 * <p>Enabled when the system property {@code jvn.threadGuard} is set to {@code true}.
 * No-ops in production builds to avoid any runtime overhead.</p>
 *
 * <pre>{@code
 * // Add to JVM args during development:
 * -Djvn.threadGuard=true
 * }</pre>
 */
public final class RenderThreadGuard {

  private static final boolean ENABLED =
      Boolean.getBoolean("jvn.threadGuard");

  private RenderThreadGuard() {}

  /**
   * Assert that the calling code is running on the JavaFX Application Thread.
   *
   * @param context a short description of the call site, included in the error message
   * @throws IllegalStateException if thread guard is enabled and the caller is off the FX thread
   */
  public static void requireFxThread(String context) {
    if (ENABLED && !Platform.isFxApplicationThread()) {
      throw new IllegalStateException(
          "RenderThreadGuard: '" + context + "' must run on the JavaFX Application Thread, "
          + "but was called from " + Thread.currentThread().getName());
    }
  }

  /**
   * Assert that the calling code is running on the JavaFX Application Thread.
   * Uses the caller's simple class name as context.
   *
   * @throws IllegalStateException if thread guard is enabled and the caller is off the FX thread
   */
  public static void requireFxThread() {
    if (ENABLED && !Platform.isFxApplicationThread()) {
      String caller = Thread.currentThread().getStackTrace().length > 2
          ? Thread.currentThread().getStackTrace()[2].getClassName()
          : "unknown";
      throw new IllegalStateException(
          "RenderThreadGuard: must run on the JavaFX Application Thread, "
          + "but was called from thread '" + Thread.currentThread().getName()
          + "' in " + caller);
    }
  }

  /** @return true if thread guard checks are active */
  public static boolean isEnabled() {
    return ENABLED;
  }
}
