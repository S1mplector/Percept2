package com.jvn.scripting.jes;

public class JesToken {
  public final JesTokenType type;
  public final String lexeme;
  public final int line;
  public final int col;

  public JesToken(JesTokenType type, String lexeme, int line, int col) {
    this.type = type;
    this.lexeme = lexeme;
    this.line = line;
    this.col = col;
  }

  @Override public String toString() { return type + "('" + lexeme + "')@" + line + ":" + col; }
}
