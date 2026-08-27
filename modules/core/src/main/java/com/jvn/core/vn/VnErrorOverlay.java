package com.jvn.core.vn;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import com.jvn.core.vn.script.MultipleParseErrorsException;
import com.jvn.core.vn.script.VnParseException;

/**
 * Holds information for a full-screen error overlay displayed when a VNS script
 * encounters a fatal or significant error during parsing or execution.
 * Inspired by Ren'Py's traceback screen.
 */
public class VnErrorOverlay {

  public enum ErrorType {
    PARSE_ERROR,
    DSL_PARSE_ERROR,
    RUNTIME_ERROR,
    DSL_RUNTIME_ERROR,
    COMPILATION_ERROR,
    INTEROP_ERROR
  }

  private final ErrorType type;
  private final String title;
  private final String message;
  private final String sourceName;
  private final int lineNumber;
  private final String likelyCause;
  private final String rawLine;
  private final String stackTrace;
  private final long timestamp;

  public VnErrorOverlay(ErrorType type, String title, String message,
                        String sourceName, int lineNumber, String stackTrace) {
    this(type, title, message, sourceName, lineNumber, null, null, stackTrace);
  }

  public VnErrorOverlay(ErrorType type, String title, String message,
                        String sourceName, int lineNumber,
                        String likelyCause, String rawLine, String stackTrace) {
    this.type = type;
    this.title = title;
    this.message = message;
    this.sourceName = sourceName;
    this.lineNumber = lineNumber;
    this.likelyCause = likelyCause;
    this.rawLine = rawLine;
    this.stackTrace = stackTrace;
    this.timestamp = System.currentTimeMillis();
  }

  public ErrorType getType() { return type; }
  public String getTitle() { return title; }
  public String getMessage() { return message; }
  public String getSourceName() { return sourceName; }
  public int getLineNumber() { return lineNumber; }
  public String getLikelyCause() { return likelyCause; }
  public String getRawLine() { return rawLine; }
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

  public static VnErrorOverlay parseError(String source, int line, String message,
                                          String likelyCause, String rawLine, String stackTrace) {
    return new VnErrorOverlay(
        ErrorType.PARSE_ERROR,
        "VNS Parse Error",
        message,
        source, line, likelyCause, rawLine, stackTrace);
  }

  public static VnErrorOverlay dslParseError(String dslName, String source, int line, int column,
                                             String message, String rawLine, Throwable cause) {
    String name = normalizeDslName(dslName);
    String locationMessage = message == null ? "Unknown parse error" : message;
    if (column > 0 && !locationMessage.contains(":" + column)) {
      locationMessage += " (column " + column + ")";
    }
    return new VnErrorOverlay(
        ErrorType.DSL_PARSE_ERROR,
        name + " Parse Error",
        locationMessage,
        source,
        line,
        likelyDslCause(name, locationMessage, rawLine),
        rawLine,
        formatStackTrace(cause));
  }

  public static VnErrorOverlay dslRuntimeError(String dslName, String source, int line,
                                               String message, Throwable cause) {
    String name = normalizeDslName(dslName);
    return new VnErrorOverlay(
        ErrorType.DSL_RUNTIME_ERROR,
        name + " Runtime Error",
        message == null || message.isBlank() ? "Unknown runtime error" : message,
        source,
        line,
        null,
        null,
        formatStackTrace(cause));
  }

  public static VnErrorOverlay jesParseError(String sourceName, String source, Throwable cause) {
    int line = readIntMethod(cause, "getLine", -1);
    int column = readIntMethod(cause, "getCol", -1);
    return dslParseError(
        "JES",
        sourceName,
        line,
        column,
        safeMessage(cause),
        lineFromSource(source, line),
        cause);
  }

  public static VnErrorOverlay puppeteerJesParseError(String sourceName, String source, Throwable cause) {
    int line = readIntMethod(cause, "getLine", -1);
    int column = readIntMethod(cause, "getCol", -1);
    return dslParseError(
        "Puppeteer JES",
        sourceName,
        line,
        column,
        safeMessage(cause),
        lineFromSource(source, line),
        cause);
  }

  public static VnErrorOverlay puppeteerTimelineTargetError(String sourceName, int line,
                                                            String message, String likelyCause,
                                                            String rawTimeline) {
    return new VnErrorOverlay(
        ErrorType.DSL_RUNTIME_ERROR,
        "Puppeteer Timeline Target Error",
        message == null || message.isBlank() ? "Timeline target is not visible" : message,
        sourceName,
        line,
        likelyCause,
        rawTimeline,
        null);
  }

  public static VnErrorOverlay puppeteerTimelineDiagnosticsError(String sourceName, int line,
                                                                 String message, String likelyCause,
                                                                 String rawTimeline) {
    return new VnErrorOverlay(
        ErrorType.DSL_RUNTIME_ERROR,
        "Puppeteer Timeline Diagnostics",
        message == null || message.isBlank() ? "Timeline validation blocked unsafe playback" : message,
        sourceName,
        line,
        likelyCause,
        rawTimeline,
        null);
  }

  public static VnErrorOverlay fromScriptLoadFailure(String fallbackSource, Exception cause) {
    if (cause instanceof MultipleParseErrorsException multi) {
      return fromMultipleParseErrors(fallbackSource, multi);
    }
    if (cause instanceof VnParseException parse) {
      return fromParseException(fallbackSource, parse, cause);
    }
    return runtimeError(
        fallbackSource,
        -1,
        cause == null ? "Unknown script loading failure" : safeMessage(cause),
        cause);
  }

  private static VnErrorOverlay fromMultipleParseErrors(String fallbackSource, MultipleParseErrorsException multi) {
    List<VnParseException> errors = multi.getErrors();
    VnParseException primary = errors == null || errors.isEmpty() ? null : errors.get(0);
    String source = primary != null ? firstNonBlank(primary.getSourceName(), fallbackSource) : fallbackSource;
    int line = primary != null ? primary.getLineNumber() : -1;
    String message = primary != null ? firstNonBlank(primary.getDetailMessage(), primary.getMessage()) : safeMessage(multi);
    String rawLine = primary != null ? primary.getRawLine() : null;
    return parseError(source, line, message, likelyCause(message, rawLine), rawLine, formatStackTrace(multi));
  }

  private static VnErrorOverlay fromParseException(String fallbackSource, VnParseException parse, Exception cause) {
    String source = firstNonBlank(parse.getSourceName(), fallbackSource);
    String message = firstNonBlank(parse.getDetailMessage(), parse.getMessage());
    return parseError(source, parse.getLineNumber(), message,
        likelyCause(message, parse.getRawLine()), parse.getRawLine(), formatStackTrace(cause));
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

  private static String formatStackTrace(Throwable e) {
    if (e == null) return null;
    StringWriter out = new StringWriter();
    e.printStackTrace(new PrintWriter(out));
    return out.toString();
  }

  private static String likelyCause(String message, String rawLine) {
    String msg = message == null ? "" : message.toLowerCase();
    String line = rawLine == null ? "" : rawLine.trim();
    if (msg.contains("unrecognized syntax")) {
      if (!line.isEmpty() && !line.startsWith("[") && !line.startsWith("@") && !line.startsWith(">") && !line.contains(":")) {
        return "This line is bare text. VNS dialogue needs a speaker prefix such as 'narrator:' or a bracketed command such as [show ...].";
      }
      return "The parser could not match this line to a VNS command, label, choice, or dialogue statement.";
    }
    if (msg.contains("unknown command")) {
      return "The bracketed command name is not registered. Check the command spelling and arguments.";
    }
    if (msg.contains("unknown character position")) {
      return "A [show] or [move] command used a position name that is not built in or declared with @position.";
    }
    if (msg.contains("duplicate label")) {
      return "Two labels use the same name. Label names must be unique within the loaded script set.";
    }
    if (msg.contains("label") && msg.contains("not found")) {
      return "A jump, goto, choice, or call points to a label that was not declared.";
    }
    return "The script could not be parsed before the game scene was created.";
  }

  private static String likelyDslCause(String dslName, String message, String rawLine) {
    String name = dslName == null ? "DSL" : dslName;
    String msg = message == null ? "" : message.toLowerCase();
    String line = rawLine == null ? "" : rawLine.trim();
    if (msg.contains("expected")) {
      return name + " syntax did not match the parser's expected token here. Check braces, quotes, colons, and block nesting around this line.";
    }
    if (msg.contains("unterminated string")) {
      return "A quoted string was opened but not closed before the end of the line or file.";
    }
    if (msg.contains("unexpected character")) {
      return "This character is not valid in this " + name + " context. Check for stray punctuation or pasted formatting.";
    }
    if (msg.contains("unknown timeline action")) {
      return "The timeline action name is not registered. Check the action spelling or use an event/call action for custom behavior.";
    }
    if (!line.isEmpty() && line.endsWith("{")) {
      return "This block opened successfully, so the issue is likely inside the block or at its closing brace.";
    }
    return name + " could not be parsed before the preview/runtime scene was created.";
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    return second == null ? "" : second;
  }

  private static String safeMessage(Throwable cause) {
    if (cause == null) return "Unknown error";
    String message = cause.getMessage();
    return message == null || message.isBlank() ? cause.toString() : message;
  }

  private static String normalizeDslName(String dslName) {
    return dslName == null || dslName.isBlank() ? "DSL" : dslName.trim();
  }

  private static String lineFromSource(String source, int lineNumber) {
    if (source == null || lineNumber <= 0) return null;
    String[] lines = source.split("\r\n|\r|\n", -1);
    if (lineNumber > lines.length) return null;
    return lines[lineNumber - 1];
  }

  private static int readIntMethod(Throwable cause, String methodName, int fallback) {
    if (cause == null || methodName == null || methodName.isBlank()) return fallback;
    try {
      Object value = cause.getClass().getMethod(methodName).invoke(cause);
      if (value instanceof Number n) return n.intValue();
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return fallback;
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
    if (rawLine != null && !rawLine.isBlank()) {
      sb.append("Script line: ").append(rawLine).append("\n");
    }
    sb.append("\nCause: ").append(message != null ? message : "(unknown)");
    if (likelyCause != null && !likelyCause.isBlank()) {
      sb.append("\n\nLikely cause: ").append(likelyCause);
    }
    if (stackTrace != null && !stackTrace.isEmpty()) {
      sb.append("\n\n--- Stack Trace ---\n").append(stackTrace);
    }
    return sb.toString();
  }
}
