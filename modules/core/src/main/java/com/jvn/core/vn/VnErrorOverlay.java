package com.jvn.core.vn;

/**
 * Holds information for a full-screen error overlay displayed when a VNS script
 * encounters a fatal or significant error during parsing or execution.
 * Inspired by Ren'Py's traceback screen.
 */
public class VnErrorOverlay {

  public enum ErrorType {
    PARSE_ERROR,
    RUNTIME_ERROR,
    COMPILATION_ERROR,
    INTEROP_ERROR
  }

  private final ErrorType type;
  private final String title;
  private final String message;
  private final String sourceName;
  private final int lineNumber;
  private final String stackTrace;
  private final long timestamp;

  public VnErrorOverlay(ErrorType type, String title, String message,
                        String sourceName, int lineNumber, String stackTrace) {
    this.type = type;
    this.title = title;
    this.message = message;
    this.sourceName = sourceName;
    this.lineNumber = lineNumber;
    this.stackTrace = stackTrace;
    this.timestamp = System.currentTimeMillis();
  }

  public ErrorType getType() { return type; }
  public String getTitle() { return title; }
  public String getMessage() { return message; }
  public String getSourceName() { return sourceName; }
  public int getLineNumber() { return lineNumber; }
  public String getStackTrace() { return stackTrace; }
  public long getTimestamp() { return timestamp; }

  // --- Factory methods ---

  public static VnErrorOverlay parseError(String source, int line, String message) {
    return new VnErrorOverlay(
        ErrorType.PARSE_ERROR,
        "VNS Parse Error",
        message,
        source, line, null);
  }

  public static VnErrorOverlay parseError(String source, int line, String message, String stackTrace) {
    return new VnErrorOverlay(
        ErrorType.PARSE_ERROR,
        "VNS Parse Error",
        message,
        source, line, stackTrace);
  }

  public static VnErrorOverlay runtimeError(String message, Exception cause) {
    return new VnErrorOverlay(
        ErrorType.RUNTIME_ERROR,
        "VNS Runtime Error",
        message,
        null, -1, formatStackTrace(cause));
  }

  public static VnErrorOverlay runtimeError(String source, int line, String message, Exception cause) {
    return new VnErrorOverlay(
        ErrorType.RUNTIME_ERROR,
        "VNS Runtime Error",
        message,
        source, line, formatStackTrace(cause));
  }

  public static VnErrorOverlay compilationError(String source, int line, String message) {
    return new VnErrorOverlay(
        ErrorType.COMPILATION_ERROR,
        "Java Compilation Error",
        message,
        source, line, null);
  }

  public static VnErrorOverlay interopError(String provider, String message, Exception cause) {
    return new VnErrorOverlay(
        ErrorType.INTEROP_ERROR,
        "VNS Interop Error (" + provider + ")",
        message,
        null, -1, formatStackTrace(cause));
  }

  private static String formatStackTrace(Exception e) {
    if (e == null) return null;
    StringBuilder sb = new StringBuilder();
    sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
    StackTraceElement[] trace = e.getStackTrace();
    int limit = Math.min(trace.length, 12);
    for (int i = 0; i < limit; i++) {
      sb.append("  at ").append(trace[i]).append("\n");
    }
    if (trace.length > limit) {
      sb.append("  ... ").append(trace.length - limit).append(" more\n");
    }
    if (e.getCause() != null && e.getCause() != e) {
      sb.append("Caused by: ").append(e.getCause().getClass().getName())
          .append(": ").append(e.getCause().getMessage()).append("\n");
    }
    return sb.toString();
  }

  /** Summary for display — combines title, location, and message. */
  public String toDisplaySummary() {
    StringBuilder sb = new StringBuilder();
    sb.append(title).append("\n\n");
    if (sourceName != null && !sourceName.isEmpty()) {
      sb.append("File: ").append(sourceName).append("\n");
    }
    if (lineNumber > 0) {
      sb.append("Line: ").append(lineNumber).append("\n");
    }
    sb.append("\nCause: ").append(message != null ? message : "(unknown)");
    if (stackTrace != null && !stackTrace.isEmpty()) {
      sb.append("\n\n--- Stack Trace ---\n").append(stackTrace);
    }
    return sb.toString();
  }
}
