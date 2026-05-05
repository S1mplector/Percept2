package com.jvn.core.vn;

import java.util.List;
import java.util.Map;

/**
 * Evaluates boolean VN condition expressions used by choices and flow commands.
 *
 * <p>Supported operators:
 * <ul>
 *   <li>Logical: {@code &&}, {@code ||}, {@code !}, {@code and}, {@code or}, {@code not}</li>
 *   <li>Comparison: {@code ==}, {@code !=}, {@code >}, {@code <}, {@code >=}, {@code <=}</li>
 *   <li>Grouping: parentheses</li>
 * </ul>
 *
 * <p>Literals: numbers, booleans ({@code true}/{@code false}), quoted strings.
 * Identifiers are resolved from the provided variables map.</p>
 */
public final class VnConditionEvaluator {
  private enum UnknownValue {
    INSTANCE
  }

  private VnConditionEvaluator() {
  }

  public static boolean evaluate(String expression, Map<String, ?> variables) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("Condition expression is empty");
    }
    Parser parser = new Parser(expression, variables == null ? Map.of() : variables, false);
    return asBoolean(parser.parseExpression());
  }

  public static Object evaluateValue(String expression, Map<String, ?> variables) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("Condition expression is empty");
    }
    Parser parser = new Parser(expression, variables == null ? Map.of() : variables, false);
    return parser.parseExpression();
  }

  public static void validate(String expression) {
    if (expression == null || expression.isBlank()) {
      throw new IllegalArgumentException("Condition expression is empty");
    }
    Parser parser = new Parser(expression, Map.of(), true);
    parser.parseExpression();
  }

  private static double asNumber(Object value) {
    if (value == null) return 0.0;
    if (value == UnknownValue.INSTANCE) return 0.0;
    if (value instanceof Number n) return n.doubleValue();
    String s = String.valueOf(value).trim();
    if (s.isEmpty()) return 0.0;
    try {
      return Double.parseDouble(s);
    } catch (Exception ignored) {
      return 0.0;
    }
  }

  private static boolean asBoolean(Object value) {
    if (value == null) return false;
    if (value == UnknownValue.INSTANCE) return false;
    if (value instanceof Boolean b) return b;
    if (value instanceof Number n) return Math.abs(n.doubleValue()) > 1e-12;
    String s = String.valueOf(value).trim();
    if (s.isEmpty()) return false;
    if ("true".equalsIgnoreCase(s)) return true;
    if ("false".equalsIgnoreCase(s)) return false;
    try {
      return Math.abs(Double.parseDouble(s)) > 1e-12;
    } catch (Exception ignored) {
      return true;
    }
  }

  private static boolean equalsValue(Object left, Object right) {
    if (left == right) return true;
    if (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE) return false;
    if (left == null || right == null) return false;
    if (left instanceof Number ln && right instanceof Number rn) {
      return Double.compare(ln.doubleValue(), rn.doubleValue()) == 0;
    }
    if (left instanceof Boolean lb && right instanceof Boolean rb) {
      return lb == rb;
    }
    return String.valueOf(left).equals(String.valueOf(right));
  }

  private static int compareValue(Object left, Object right) {
    if (left instanceof Number ln && right instanceof Number rn) {
      return Double.compare(ln.doubleValue(), rn.doubleValue());
    }
    if (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE) {
      throw new IllegalArgumentException("Cannot compare unresolved identifiers with relational operators");
    }
    if (left == null || right == null) {
      throw new IllegalArgumentException("Cannot compare null values with relational operators");
    }
    return String.valueOf(left).compareTo(String.valueOf(right));
  }

  private enum TokenType {
    LPAREN, RPAREN,
    OP_EQ, OP_NE, OP_GT, OP_LT, OP_GE, OP_LE,
    OP_AND, OP_OR, OP_NOT,
    OP_ADD, OP_SUB, OP_MUL, OP_DIV, OP_MOD,
    NUMBER, STRING, BOOLEAN, IDENT,
    END
  }

  private static final class Token {
    final TokenType type;
    final String text;
    final int offset;

    Token(TokenType type, String text, int offset) {
      this.type = type;
      this.text = text;
      this.offset = offset;
    }
  }

  private static final class Lexer {
    private final String input;
    private int index = 0;

    Lexer(String input) {
      this.input = input == null ? "" : input;
    }

    List<Token> tokenize() {
      java.util.ArrayList<Token> tokens = new java.util.ArrayList<>();
      Token t;
      do {
        t = next(tokens.isEmpty() ? null : tokens.get(tokens.size() - 1));
        tokens.add(t);
      } while (t.type != TokenType.END);
      return tokens;
    }

    private boolean isValueToken(Token prev) {
      if (prev == null) return false;
      return prev.type == TokenType.NUMBER || prev.type == TokenType.IDENT
          || prev.type == TokenType.STRING || prev.type == TokenType.BOOLEAN
          || prev.type == TokenType.RPAREN;
    }

    private Token next(Token prev) {
      skipWs();
      if (index >= input.length()) return new Token(TokenType.END, "", index);

      int start = index;
      char c = input.charAt(index);
      char n = index + 1 < input.length() ? input.charAt(index + 1) : '\0';

      if (c == '(') {
        index++;
        return new Token(TokenType.LPAREN, "(", start);
      }
      if (c == ')') {
        index++;
        return new Token(TokenType.RPAREN, ")", start);
      }
      if (c == '=' && n == '=') {
        index += 2;
        return new Token(TokenType.OP_EQ, "==", start);
      }
      if (c == '!' && n == '=') {
        index += 2;
        return new Token(TokenType.OP_NE, "!=", start);
      }
      if (c == '>' && n == '=') {
        index += 2;
        return new Token(TokenType.OP_GE, ">=", start);
      }
      if (c == '<' && n == '=') {
        index += 2;
        return new Token(TokenType.OP_LE, "<=", start);
      }
      if (c == '&' && n == '&') {
        index += 2;
        return new Token(TokenType.OP_AND, "&&", start);
      }
      if (c == '|' && n == '|') {
        index += 2;
        return new Token(TokenType.OP_OR, "||", start);
      }
      if (c == '!') {
        index++;
        return new Token(TokenType.OP_NOT, "!", start);
      }
      if (c == '>') {
        index++;
        return new Token(TokenType.OP_GT, ">", start);
      }
      if (c == '<') {
        index++;
        return new Token(TokenType.OP_LT, "<", start);
      }
      if (c == '+') {
        index++;
        return new Token(TokenType.OP_ADD, "+", start);
      }
      if (c == '*') {
        index++;
        return new Token(TokenType.OP_MUL, "*", start);
      }
      if (c == '/') {
        index++;
        return new Token(TokenType.OP_DIV, "/", start);
      }
      if (c == '%') {
        index++;
        return new Token(TokenType.OP_MOD, "%", start);
      }
      if (c == '"' || c == '\'') {
        return readString(c, start);
      }
      // '-': subtraction after a value token, negative literal otherwise
      if (c == '-') {
        if (isValueToken(prev)) {
          index++;
          return new Token(TokenType.OP_SUB, "-", start);
        }
        if (index + 1 < input.length() && Character.isDigit(input.charAt(index + 1))) {
          return readNumber(start);
        }
        index++;
        return new Token(TokenType.OP_SUB, "-", start);
      }
      if (Character.isDigit(c)) {
        return readNumber(start);
      }
      if (isIdentStart(c)) {
        return readWord(start);
      }
      throw error("Unexpected character '" + c + "'", start);
    }

    private Token readString(char quote, int start) {
      index++; // opening quote
      StringBuilder out = new StringBuilder();
      boolean escaped = false;
      while (index < input.length()) {
        char c = input.charAt(index++);
        if (escaped) {
          switch (c) {
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 't' -> out.append('\t');
            case '\\' -> out.append('\\');
            case '"' -> out.append('"');
            case '\'' -> out.append('\'');
            default -> out.append(c);
          }
          escaped = false;
          continue;
        }
        if (c == '\\') {
          escaped = true;
          continue;
        }
        if (c == quote) {
          return new Token(TokenType.STRING, out.toString(), start);
        }
        out.append(c);
      }
      throw error("Unterminated string literal", start);
    }

    private Token readNumber(int start) {
      int i = index;
      if (input.charAt(i) == '-') i++;
      while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
      if (i < input.length() && input.charAt(i) == '.') {
        i++;
        while (i < input.length() && Character.isDigit(input.charAt(i))) i++;
      }
      String text = input.substring(index, i);
      index = i;
      return new Token(TokenType.NUMBER, text, start);
    }

    private Token readWord(int start) {
      int i = index;
      while (i < input.length() && isIdentPart(input.charAt(i))) i++;
      String word = input.substring(index, i);
      index = i;

      if ("true".equalsIgnoreCase(word) || "false".equalsIgnoreCase(word)) {
        return new Token(TokenType.BOOLEAN, word, start);
      }
      if ("and".equalsIgnoreCase(word)) {
        return new Token(TokenType.OP_AND, word, start);
      }
      if ("or".equalsIgnoreCase(word)) {
        return new Token(TokenType.OP_OR, word, start);
      }
      if ("not".equalsIgnoreCase(word)) {
        return new Token(TokenType.OP_NOT, word, start);
      }
      return new Token(TokenType.IDENT, word, start);
    }

    private void skipWs() {
      while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
        index++;
      }
    }

    private boolean isIdentStart(char c) {
      return Character.isLetter(c) || c == '_' || c == '$';
    }

    private boolean isIdentPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.' || c == '-';
    }

    private IllegalArgumentException error(String message, int offset) {
      return new IllegalArgumentException(message + " at column " + (offset + 1));
    }
  }

  private static final class Parser {
    private final String input;
    private final Map<String, ?> vars;
    private final boolean syntaxOnly;
    private final List<Token> tokens;
    private int cursor = 0;

    Parser(String input, Map<String, ?> vars, boolean syntaxOnly) {
      this.input = input;
      this.vars = vars;
      this.syntaxOnly = syntaxOnly;
      this.tokens = new Lexer(input).tokenize();
    }

    Object parseExpression() {
      Object value = parseOr();
      Token end = peek();
      if (end.type != TokenType.END) {
        throw error("Unexpected token '" + end.text + "'", end);
      }
      return value;
    }

    private Object parseOr() {
      Object left = parseAnd();
      while (match(TokenType.OP_OR)) {
        Object right = parseAnd();
        left = asBoolean(left) || asBoolean(right);
      }
      return left;
    }

    private Object parseAnd() {
      Object left = parseEquality();
      while (match(TokenType.OP_AND)) {
        Object right = parseEquality();
        left = asBoolean(left) && asBoolean(right);
      }
      return left;
    }

    private Object parseEquality() {
      Object left = parseComparison();
      while (true) {
        if (match(TokenType.OP_EQ)) {
          Object right = parseComparison();
          if (syntaxOnly && (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE)) {
            left = false;
          } else {
            left = equalsValue(left, right);
          }
          continue;
        }
        if (match(TokenType.OP_NE)) {
          Object right = parseComparison();
          if (syntaxOnly && (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE)) {
            left = true;
          } else {
            left = !equalsValue(left, right);
          }
          continue;
        }
        return left;
      }
    }

    private Object parseComparison() {
      Object left = parseAdditive();
      while (true) {
        if (match(TokenType.OP_GT)) {
          Object right = parseUnary();
          if (syntaxOnly && (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE)) {
            left = false;
          } else {
            left = compareValue(left, right) > 0;
          }
          continue;
        }
        if (match(TokenType.OP_LT)) {
          Object right = parseAdditive();
          if (syntaxOnly && (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE)) {
            left = false;
          } else {
            left = compareValue(left, right) < 0;
          }
          continue;
        }
        if (match(TokenType.OP_GE)) {
          Object right = parseAdditive();
          if (syntaxOnly && (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE)) {
            left = false;
          } else {
            left = compareValue(left, right) >= 0;
          }
          continue;
        }
        if (match(TokenType.OP_LE)) {
          Object right = parseAdditive();
          if (syntaxOnly && (left == UnknownValue.INSTANCE || right == UnknownValue.INSTANCE)) {
            left = false;
          } else {
            left = compareValue(left, right) <= 0;
          }
          continue;
        }
        return left;
      }
    }

    private Object parseAdditive() {
      Object left = parseMultiplicative();
      while (true) {
        if (match(TokenType.OP_ADD)) {
          Object right = parseMultiplicative();
          if (left instanceof String || right instanceof String) {
            left = String.valueOf(left) + String.valueOf(right);
          } else {
            left = asNumber(left) + asNumber(right);
          }
          continue;
        }
        if (match(TokenType.OP_SUB)) {
          Object right = parseMultiplicative();
          left = asNumber(left) - asNumber(right);
          continue;
        }
        return left;
      }
    }

    private Object parseMultiplicative() {
      Object left = parseUnary();
      while (true) {
        if (match(TokenType.OP_MUL)) {
          Object right = parseUnary();
          left = asNumber(left) * asNumber(right);
          continue;
        }
        if (match(TokenType.OP_DIV)) {
          Object right = parseUnary();
          double divisor = asNumber(right);
          if (Math.abs(divisor) < 1e-15) {
            throw new IllegalArgumentException("Division by zero");
          }
          left = asNumber(left) / divisor;
          continue;
        }
        if (match(TokenType.OP_MOD)) {
          Object right = parseUnary();
          double divisor = asNumber(right);
          if (Math.abs(divisor) < 1e-15) {
            throw new IllegalArgumentException("Modulo by zero");
          }
          left = asNumber(left) % divisor;
          continue;
        }
        return left;
      }
    }

    private Object parseUnary() {
      if (match(TokenType.OP_NOT)) {
        return !asBoolean(parseUnary());
      }
      if (match(TokenType.OP_SUB)) {
        return -asNumber(parseUnary());
      }
      return parsePrimary();
    }

    private Object parsePrimary() {
      Token t = peek();
      switch (t.type) {
        case NUMBER:
          consume();
          try {
            return t.text.contains(".") ? Double.parseDouble(t.text) : Long.parseLong(t.text);
          } catch (NumberFormatException ex) {
            throw error("Invalid number '" + t.text + "'", t);
          }
        case STRING:
          consume();
          return t.text;
        case BOOLEAN:
          consume();
          return Boolean.parseBoolean(t.text.toLowerCase());
        case IDENT:
          consume();
          if (vars.containsKey(t.text)) return vars.get(t.text);
          return syntaxOnly ? UnknownValue.INSTANCE : null;
        case LPAREN:
          consume();
          Object value = parseOr();
          Token close = peek();
          if (close.type != TokenType.RPAREN) {
            throw error("Expected ')'", close);
          }
          consume();
          return value;
        default:
          throw error("Unexpected token '" + t.text + "'", t);
      }
    }

    private Token peek() {
      if (cursor < 0 || cursor >= tokens.size()) return new Token(TokenType.END, "", input.length());
      return tokens.get(cursor);
    }

    private void consume() {
      if (cursor < tokens.size()) cursor++;
    }

    private boolean match(TokenType type) {
      Token t = peek();
      if (t.type != type) return false;
      consume();
      return true;
    }

    private IllegalArgumentException error(String message, Token token) {
      int col = (token == null ? input.length() : token.offset) + 1;
      return new IllegalArgumentException(message + " at column " + col);
    }
  }
}
