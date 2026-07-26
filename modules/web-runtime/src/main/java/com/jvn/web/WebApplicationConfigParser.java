package com.jvn.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.config.ApplicationConfig;

/**
 * Minimal JSON reader for the web launcher's {@link ApplicationConfig} boundary.
 *
 * <p>The implementation intentionally uses only JDK collection and language types so the web
 * runtime does not acquire a reflection-heavy JSON dependency before a Java-to-web toolchain is
 * selected.</p>
 */
final class WebApplicationConfigParser {
  private static final Object JSON_NULL = new Object();
  private static final double MAX_EXACT_DOUBLE_INTEGER = 9_007_199_254_740_991.0;

  static final String DEFAULT_TITLE = "JVN Game";
  static final int DEFAULT_WIDTH = 1280;
  static final int DEFAULT_HEIGHT = 720;
  static final long DEFAULT_FIXED_UPDATE_MS = 0L;
  static final int DEFAULT_FIXED_UPDATE_MAX_STEPS = 5;
  static final double DEFAULT_TIME_SCALE = 1.0;

  private WebApplicationConfigParser() {}

  static ApplicationConfig parse(String configJson) {
    Map<String, Object> values = configJson == null || configJson.isBlank()
        ? Map.of()
        : new JsonParser(configJson).parseDocument();

    String title = stringValue(values, "title", DEFAULT_TITLE);
    int width = positiveIntValue(values, "width", DEFAULT_WIDTH);
    int height = positiveIntValue(values, "height", DEFAULT_HEIGHT);
    long fixedUpdateMs = nonNegativeLongValue(
        values, "fixedUpdateMs", DEFAULT_FIXED_UPDATE_MS);
    int fixedUpdateMaxSteps = positiveIntValue(
        values, "fixedUpdateMaxSteps", DEFAULT_FIXED_UPDATE_MAX_STEPS);
    double timeScale = boundedDoubleValue(
        values, "timeScale", DEFAULT_TIME_SCALE, 0.0, 10.0);

    return ApplicationConfig.builder()
        .title(title)
        .width(width)
        .height(height)
        .fixedUpdate(fixedUpdateMs, fixedUpdateMaxSteps)
        .timeScale(timeScale)
        .build();
  }

  private static String stringValue(
      Map<String, Object> values, String key, String defaultValue) {
    if (!values.containsKey(key)) return defaultValue;
    Object value = values.get(key);
    if (value instanceof String text) return text;
    throw invalidValue(key, "must be a string");
  }

  private static int positiveIntValue(
      Map<String, Object> values, String key, int defaultValue) {
    if (!values.containsKey(key)) return defaultValue;
    long value = wholeLongValue(values.get(key), key);
    if (value <= 0L || value > Integer.MAX_VALUE) {
      throw invalidValue(key, "must be a positive 32-bit integer");
    }
    return (int) value;
  }

  private static long nonNegativeLongValue(
      Map<String, Object> values, String key, long defaultValue) {
    if (!values.containsKey(key)) return defaultValue;
    long value = wholeLongValue(values.get(key), key);
    if (value < 0L) {
      throw invalidValue(key, "must be a non-negative integer");
    }
    return value;
  }

  private static long wholeLongValue(Object value, String key) {
    if (value instanceof Long integer) return integer;
    if (value instanceof Double decimal
        && Double.isFinite(decimal)
        && Math.abs(decimal) <= MAX_EXACT_DOUBLE_INTEGER
        && decimal == Math.rint(decimal)) {
      return decimal.longValue();
    }
    throw invalidValue(key, "must be an integer");
  }

  private static double boundedDoubleValue(
      Map<String, Object> values,
      String key,
      double defaultValue,
      double minimum,
      double maximum) {
    if (!values.containsKey(key)) return defaultValue;
    Object raw = values.get(key);
    if (!(raw instanceof Number number)) {
      throw invalidValue(key, "must be a number");
    }
    double value = number.doubleValue();
    if (!Double.isFinite(value) || value < minimum || value > maximum) {
      throw invalidValue(key, "must be between " + minimum + " and " + maximum);
    }
    return value;
  }

  private static IllegalArgumentException invalidValue(String key, String detail) {
    return new IllegalArgumentException("Invalid web configuration field '" + key + "': " + detail);
  }

  private static final class JsonParser {
    private final String input;
    private int index;

    private JsonParser(String input) {
      this.input = input;
    }

    private Map<String, Object> parseDocument() {
      skipWhitespace();
      if (peek() != '{') {
        throw error("configuration root must be a JSON object");
      }
      Map<String, Object> result = parseObject();
      skipWhitespace();
      if (!isAtEnd()) {
        throw error("unexpected trailing content");
      }
      return result;
    }

    private Map<String, Object> parseObject() {
      expect('{');
      Map<String, Object> result = new LinkedHashMap<>();
      skipWhitespace();
      if (consume('}')) return result;

      while (true) {
        skipWhitespace();
        if (peek() != '"') throw error("expected a quoted object key");
        String key = parseString();
        if (result.containsKey(key)) {
          throw error("duplicate object key '" + key + "'");
        }
        skipWhitespace();
        expect(':');
        Object value = parseValue();
        result.put(key, value);
        skipWhitespace();
        if (consume('}')) return result;
        expect(',');
      }
    }

    private List<Object> parseArray() {
      expect('[');
      List<Object> result = new ArrayList<>();
      skipWhitespace();
      if (consume(']')) return result;

      while (true) {
        result.add(parseValue());
        skipWhitespace();
        if (consume(']')) return result;
        expect(',');
      }
    }

    private Object parseValue() {
      skipWhitespace();
      char current = peek();
      return switch (current) {
        case '"' -> parseString();
        case '{' -> parseObject();
        case '[' -> parseArray();
        case 't' -> parseLiteral("true", Boolean.TRUE);
        case 'f' -> parseLiteral("false", Boolean.FALSE);
        case 'n' -> parseLiteral("null", JSON_NULL);
        default -> {
          if (current == '-' || isDigit(current)) yield parseNumber();
          throw error("expected a JSON value");
        }
      };
    }

    private Object parseLiteral(String literal, Object value) {
      if (!input.startsWith(literal, index)) {
        throw error("expected '" + literal + "'");
      }
      index += literal.length();
      return value;
    }

    private Number parseNumber() {
      int start = index;
      consume('-');

      if (consume('0')) {
        if (isDigit(peek())) throw error("leading zero is not allowed in a number");
      } else {
        requireDigit("expected a digit");
        while (isDigit(peek())) index++;
      }

      boolean decimal = false;
      if (consume('.')) {
        decimal = true;
        requireDigit("expected a digit after decimal point");
        while (isDigit(peek())) index++;
      }

      char exponentMarker = peek();
      if (exponentMarker == 'e' || exponentMarker == 'E') {
        decimal = true;
        index++;
        char sign = peek();
        if (sign == '+' || sign == '-') index++;
        requireDigit("expected a digit in exponent");
        while (isDigit(peek())) index++;
      }

      String token = input.substring(start, index);
      try {
        if (!decimal) return Long.valueOf(token);
        Double value = Double.valueOf(token);
        if (!Double.isFinite(value)) throw error("number is outside the supported range");
        return value;
      } catch (NumberFormatException ex) {
        throw error("number is outside the supported range");
      }
    }

    private String parseString() {
      expect('"');
      StringBuilder result = new StringBuilder();
      while (!isAtEnd()) {
        char current = input.charAt(index++);
        if (current == '"') return result.toString();
        if (current == '\\') {
          result.append(parseEscape());
        } else {
          if (current < 0x20) {
            throw error("unescaped control character in string");
          }
          result.append(current);
        }
      }
      throw error("unterminated string");
    }

    private char parseEscape() {
      if (isAtEnd()) throw error("unterminated escape sequence");
      char escaped = input.charAt(index++);
      return switch (escaped) {
        case '"', '\\', '/' -> escaped;
        case 'b' -> '\b';
        case 'f' -> '\f';
        case 'n' -> '\n';
        case 'r' -> '\r';
        case 't' -> '\t';
        case 'u' -> parseUnicodeEscape();
        default -> throw error("unsupported escape sequence '\\" + escaped + "'");
      };
    }

    private char parseUnicodeEscape() {
      if (index + 4 > input.length()) throw error("incomplete Unicode escape");
      int value = 0;
      for (int offset = 0; offset < 4; offset++) {
        int digit = hexValue(input.charAt(index + offset));
        if (digit < 0) throw error("invalid Unicode escape");
        value = (value << 4) | digit;
      }
      index += 4;
      return (char) value;
    }

    private void requireDigit(String message) {
      if (!isDigit(peek())) throw error(message);
    }

    private void expect(char expected) {
      skipWhitespace();
      if (!consume(expected)) throw error("expected '" + expected + "'");
    }

    private boolean consume(char expected) {
      if (!isAtEnd() && input.charAt(index) == expected) {
        index++;
        return true;
      }
      return false;
    }

    private char peek() {
      return isAtEnd() ? '\0' : input.charAt(index);
    }

    private boolean isAtEnd() {
      return index >= input.length();
    }

    private void skipWhitespace() {
      while (!isAtEnd()) {
        char current = input.charAt(index);
        if (current != ' ' && current != '\n' && current != '\r' && current != '\t') return;
        index++;
      }
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(
          "Invalid web configuration JSON at position " + index + ": " + message);
    }

    private static boolean isDigit(char value) {
      return value >= '0' && value <= '9';
    }

    private static int hexValue(char value) {
      if (value >= '0' && value <= '9') return value - '0';
      if (value >= 'a' && value <= 'f') return value - 'a' + 10;
      if (value >= 'A' && value <= 'F') return value - 'A' + 10;
      return -1;
    }
  }
}
