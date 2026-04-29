package com.jvn.core.vn.script;

import java.io.IOException;

public class VnParseException extends IOException {
  private final String sourceName;
  private final int lineNumber;
  private final String detailMessage;
  private final String rawLine;

  public VnParseException(String message, String sourceName, int lineNumber, String detailMessage, String rawLine) {
    super(message);
    this.sourceName = sourceName;
    this.lineNumber = lineNumber;
    this.detailMessage = detailMessage;
    this.rawLine = rawLine;
  }

  public String getSourceName() { return sourceName; }
  public int getLineNumber() { return lineNumber; }
  public String getDetailMessage() { return detailMessage; }
  public String getRawLine() { return rawLine; }
}
