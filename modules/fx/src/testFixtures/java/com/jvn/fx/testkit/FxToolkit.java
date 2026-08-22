package com.jvn.fx.testkit;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit exactly once for the whole test JVM and keeps it alive for the
 * duration of the run.
 *
 * <p>JUnit 5 runs all test classes for a module in a single forked JVM by default. JavaFX's
 * toolkit can only be started once per JVM, and by default shuts itself down as soon as the
 * last showing {@code Stage} closes ({@code Platform.setImplicitExit(true)}). Any test class
 * that opens and closes a real window therefore kills the toolkit for every test class that
 * happens to run afterward in the same JVM, which is what made the old per-file
 * {@code Platform.startup(...)} boilerplate flaky: it treated {@link IllegalStateException}
 * from an already-started toolkit as "available", even after the toolkit had been shut down.
 *
 * <p>This class starts the toolkit at most once and immediately disables implicit exit, so
 * closing windows in one test can never affect any other test in the same JVM.
 */
public final class FxToolkit {
  private static final Object LOCK = new Object();
  private static boolean started;
  private static boolean available;

  private FxToolkit() {}

  /** Starts the JavaFX toolkit if it hasn't been started yet in this JVM. Safe to call from every test class. */
  public static boolean ensureStarted() {
    synchronized (LOCK) {
      if (started) {
        return available;
      }
      started = true;
      if (isHeadlessLinux()) {
        available = false;
        return false;
      }
      CountDownLatch ready = new CountDownLatch(1);
      try {
        Platform.startup(ready::countDown);
        available = ready.await(10, TimeUnit.SECONDS);
      } catch (IllegalStateException alreadyStarted) {
        // Something outside this class (e.g. the application under test) started the
        // toolkit first. It is already running, so it is available for use.
        available = true;
      } catch (Exception unavailable) {
        available = false;
      }
      if (available) {
        Platform.setImplicitExit(false);
      }
      return available;
    }
  }

  /** Whether the toolkit started successfully. Only meaningful after {@link #ensureStarted()}. */
  public static boolean isAvailable() {
    synchronized (LOCK) {
      return started && available;
    }
  }

  private static boolean isHeadlessLinux() {
    return System.getProperty("os.name", "").toLowerCase().contains("linux")
        && System.getenv().getOrDefault("DISPLAY", "").isBlank();
  }

  /** Runs {@code work} on the JavaFX application thread and returns its result, waiting up to 30s. */
  public static <T> T runFx(Callable<T> work) throws Exception {
    FutureTask<T> task = new FutureTask<>(work);
    Platform.runLater(task);
    return task.get(30, TimeUnit.SECONDS);
  }

  /** Runs {@code work} on the JavaFX application thread, waiting up to 30s for it to finish. */
  public static void runFx(Runnable work) throws Exception {
    runFx(() -> {
      work.run();
      return null;
    });
  }
}
