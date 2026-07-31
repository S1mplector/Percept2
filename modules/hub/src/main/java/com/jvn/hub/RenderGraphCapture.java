package com.jvn.hub;

/**
 * Keeps the latest bounded JavaFX pulse/render-graph block emitted by a Hub-managed process.
 *
 * <p>JavaFX's print logger separates detailed slow-pulse reports with blank lines. The graph is
 * already formatted as an indented tree, so the Hub can present it directly without depending on
 * internal JavaFX scene-graph classes.</p>
 */
final class RenderGraphCapture {
  static final int MAX_GRAPH_CHARS = 120_000;
  private static final String TRUNCATED_SUFFIX = "\n… render graph truncated by Engine Hub …\n";

  record Snapshot(
      long revision,
      String session,
      String graphSession,
      String graph,
      int capturedGraphs,
      boolean captureEnabled,
      boolean processRunning) {
  }

  private final StringBuilder current = new StringBuilder();
  private String latest = "";
  private String session = "No managed render session yet";
  private String latestSession = "No captured render session yet";
  private long revision;
  private int capturedGraphs;
  private boolean captureEnabled;
  private boolean processRunning;
  private boolean collecting;
  private boolean truncated;

  synchronized void beginSession(String label, boolean enabled) {
    finishCurrent();
    session = label == null || label.isBlank() ? "Managed JavaFX process" : label.trim();
    captureEnabled = enabled;
    processRunning = true;
    collecting = false;
    current.setLength(0);
    truncated = false;
    revision++;
  }

  synchronized boolean accept(String line) {
    if (!captureEnabled || !processRunning || line == null) return false;
    String trimmed = line.trim();
    if (trimmed.startsWith("PULSE:")) {
      finishCurrent();
      collecting = true;
      appendLine(trimmed);
      return true;
    }
    if (!collecting) {
      // Fast pulses are printed as compact bracket runs. They do not contain a graph and would
      // otherwise cause needless Hub activity-label repaints while diagnostics are enabled.
      return trimmed.startsWith("[") && trimmed.contains("ms:") && trimmed.endsWith("]");
    }
    if (trimmed.isEmpty()) {
      finishCurrent();
      return true;
    }
    appendLine(line.stripTrailing());
    return true;
  }

  synchronized void endSession() {
    finishCurrent();
    processRunning = false;
    revision++;
  }

  synchronized void clear() {
    current.setLength(0);
    latest = "";
    capturedGraphs = 0;
    collecting = false;
    truncated = false;
    revision++;
  }

  synchronized Snapshot snapshot() {
    String graph = collecting && current.length() > 0 ? current.toString() : latest;
    return new Snapshot(
        revision,
        session,
        latestSession,
        graph,
        capturedGraphs + (collecting && current.length() > 0 ? 1 : 0),
        captureEnabled,
        processRunning);
  }

  private void appendLine(String line) {
    if (truncated) return;
    int required = line.length() + 1;
    if (current.length() + required > MAX_GRAPH_CHARS) {
      int available = Math.max(0, MAX_GRAPH_CHARS - current.length() - TRUNCATED_SUFFIX.length());
      if (available > 0) current.append(line, 0, Math.min(available, line.length()));
      current.append(TRUNCATED_SUFFIX);
      truncated = true;
    } else {
      current.append(line).append('\n');
    }
    revision++;
  }

  private void finishCurrent() {
    if (collecting && current.length() > 0) {
      latest = current.toString().stripTrailing();
      latestSession = session;
      capturedGraphs++;
    }
    current.setLength(0);
    collecting = false;
    truncated = false;
  }
}
