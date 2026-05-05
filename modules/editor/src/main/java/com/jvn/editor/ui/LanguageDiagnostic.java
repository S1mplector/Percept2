package com.jvn.editor.ui;

/**
 * Shared structured diagnostic for editor language tooling.
 *
 * <p>Line and column are zero-based for direct use with editor widgets. Use
 * {@link #oneBasedLine()} and {@link #oneBasedColumn()} for user-facing text.
 */
public record LanguageDiagnostic(
    String language,
    String sourceName,
    Severity severity,
    String code,
    String message,
    int startOffset,
    int endOffset,
    int line,
    int column) {

  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public LanguageDiagnostic {
    language = clean(language, "unknown");
    sourceName = clean(sourceName, "");
    severity = severity == null ? Severity.ERROR : severity;
    code = clean(code, "unknown");
    message = message == null ? "" : message;
    startOffset = Math.max(0, startOffset);
    endOffset = Math.max(startOffset, endOffset);
    line = Math.max(0, line);
    column = Math.max(0, column);
  }

  public static LanguageDiagnostic error(String language,
                                         String sourceName,
                                         String code,
                                         String message,
                                         int startOffset,
                                         int endOffset,
                                         int line,
                                         int column) {
    return new LanguageDiagnostic(
        language,
        sourceName,
        Severity.ERROR,
        code,
        message,
        startOffset,
        endOffset,
        line,
        column);
  }

  public static LanguageDiagnostic warning(String language,
                                           String sourceName,
                                           String code,
                                           String message,
                                           int startOffset,
                                           int endOffset,
                                           int line,
                                           int column) {
    return new LanguageDiagnostic(
        language,
        sourceName,
        Severity.WARNING,
        code,
        message,
        startOffset,
        endOffset,
        line,
        column);
  }

  public static LanguageDiagnostic info(String language,
                                        String sourceName,
                                        String code,
                                        String message,
                                        int startOffset,
                                        int endOffset,
                                        int line,
                                        int column) {
    return new LanguageDiagnostic(
        language,
        sourceName,
        Severity.INFO,
        code,
        message,
        startOffset,
        endOffset,
        line,
        column);
  }

  public boolean isError() {
    return severity == Severity.ERROR;
  }

  public boolean isWarning() {
    return severity == Severity.WARNING;
  }

  public int oneBasedLine() {
    return line + 1;
  }

  public int oneBasedColumn() {
    return column + 1;
  }

  private static String clean(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.trim();
  }
}
