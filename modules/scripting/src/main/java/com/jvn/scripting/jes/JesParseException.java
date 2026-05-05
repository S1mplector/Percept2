package com.jvn.scripting.jes;

/**
 * Parse/tokenization error that carries a line/column for diagnostics.
 */
public class JesParseException extends RuntimeException {
  private final int line;
  private final int col;

  public JesParseException(String message, int line, int col) {
    super(message + (line > 0 && col > 0 ? " at " + line + ":" + col : ""));
    this.line = line;
    this.col = col;
  }

  public int getLine() { return line; }
  public int getCol() { return col; }
}
