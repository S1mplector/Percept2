package com.jvn.core.diagnostics;

import java.time.Instant;
import java.util.List;

/**
 * Shared crash-report model written to {@code ~/.jvn/crashes/<timestamp>.json}.
 */
public final class CrashReport {

  public final String timestamp;
  public final String jvmVersion;
  public final String osName;
  public final long heapUsedMb;
  public final String thread;
  public final String exceptionType;
  public final String exceptionMessage;
  public final String stackTrace;
  public final List<String> recentLogLines;
  public final String vnState;

  private CrashReport(Builder b) {
    this.timestamp = b.timestamp;
    this.jvmVersion = b.jvmVersion;
    this.osName = b.osName;
    this.heapUsedMb = b.heapUsedMb;
    this.thread = b.thread;
    this.exceptionType = b.exceptionType;
    this.exceptionMessage = b.exceptionMessage;
    this.stackTrace = b.stackTrace;
    this.recentLogLines = b.recentLogLines;
    this.vnState = b.vnState;
  }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private String timestamp = Instant.now().toString();
    private String jvmVersion = System.getProperty("java.version", "unknown");
    private String osName = System.getProperty("os.name", "unknown");
    private long heapUsedMb;
    private String thread = "unknown";
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private List<String> recentLogLines = List.of();
    private String vnState;

    public Builder heapUsedMb(long v) { heapUsedMb = v; return this; }
    public Builder thread(String v) { thread = v; return this; }
    public Builder exceptionType(String v) { exceptionType = v; return this; }
    public Builder exceptionMessage(String v) { exceptionMessage = v; return this; }
    public Builder stackTrace(String v) { stackTrace = v; return this; }
    public Builder recentLogLines(List<String> v) { recentLogLines = v != null ? List.copyOf(v) : List.of(); return this; }
    public Builder vnState(String v) { vnState = v; return this; }
    public CrashReport build() { return new CrashReport(this); }
  }

  /** Serialize to a minimal JSON string (no external deps). */
  public String toJson() {
    StringBuilder sb = new StringBuilder("{\n");
    appendField(sb, "timestamp", timestamp);
    appendField(sb, "jvmVersion", jvmVersion);
    appendField(sb, "osName", osName);
    sb.append("  \"heapUsedMb\": ").append(heapUsedMb).append(",\n");
    appendField(sb, "thread", thread);
    appendField(sb, "exceptionType", exceptionType);
    appendField(sb, "exceptionMessage", exceptionMessage);
    appendField(sb, "stackTrace", stackTrace);
    appendField(sb, "vnState", vnState);
    sb.append("  \"recentLogLines\": [");
    if (recentLogLines != null && !recentLogLines.isEmpty()) {
      for (int i = 0; i < recentLogLines.size(); i++) {
        sb.append("\n    ").append(jsonString(recentLogLines.get(i)));
        if (i < recentLogLines.size() - 1) sb.append(",");
      }
      sb.append("\n  ");
    }
    sb.append("]\n}");
    return sb.toString();
  }

  private static void appendField(StringBuilder sb, String key, String value) {
    sb.append("  ").append(jsonString(key)).append(": ").append(jsonString(value)).append(",\n");
  }

  private static String jsonString(String s) {
    if (s == null) return "null";
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
  }
}
