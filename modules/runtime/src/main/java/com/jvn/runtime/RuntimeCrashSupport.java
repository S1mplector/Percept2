package com.jvn.runtime;

import com.jvn.core.diagnostics.CrashReport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Installs an uncaught-exception handler for the runtime process.
 *
 * <p>On crash: captures heap, stack trace, last VN state (if available),
 * writes {@code ~/.jvn/crashes/<timestamp>.json}, and logs the path.</p>
 *
 * <p>Call {@link #install()} once during application startup.</p>
 */
public final class RuntimeCrashSupport {

  private static final Logger log = LoggerFactory.getLogger(RuntimeCrashSupport.class);
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private static volatile String lastVnStateSnapshot = null;

  private RuntimeCrashSupport() {}

  /** Call once on startup to register the uncaught-exception handler. */
  public static void install() {
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      try {
        handleCrash(thread, throwable);
      } catch (Throwable secondary) {
        log.error("RuntimeCrashSupport: secondary failure writing crash report", secondary);
      }
    });
    log.debug("RuntimeCrashSupport installed");
  }

  /**
   * Store a sanitized snapshot of the current VN state for inclusion in crash reports.
   * Call this each time the VN advances to a new node.
   */
  public static void updateVnStateSnapshot(String snapshot) {
    lastVnStateSnapshot = snapshot;
  }

  private static void handleCrash(Thread thread, Throwable throwable) {
    long heapMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);

    StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));

    CrashReport report = CrashReport.builder()
        .heapUsedMb(heapMb)
        .thread(thread.getName())
        .exceptionType(throwable.getClass().getName())
        .exceptionMessage(throwable.getMessage())
        .stackTrace(sw.toString())
        .recentLogLines(List.of())
        .vnState(lastVnStateSnapshot)
        .build();

    Path crashDir = Path.of(System.getProperty("user.home"), ".jvn", "crashes");
    try {
      Files.createDirectories(crashDir);
    } catch (IOException e) {
      log.error("RuntimeCrashSupport: could not create crash dir {}", crashDir, e);
      return;
    }

    String timestamp = TS_FMT.format(Instant.now());
    Path reportFile = crashDir.resolve("runtime-" + timestamp + ".json");
    try {
      Files.writeString(reportFile, report.toJson(), StandardCharsets.UTF_8);
      log.error("RuntimeCrashSupport: crash report written to {}", reportFile);
    } catch (IOException e) {
      log.error("RuntimeCrashSupport: could not write crash report", e);
    }
  }
}
