package com.jvn.core.vn;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class VnConditionEvaluatorTest {
  @Test
  void evaluatesNumericStringAndBooleanExpressions() {
    Map<String, Object> vars = Map.of(
        "courage", 2,
        "playerName", "Ari",
        "flag", true
    );

    assertTrue(VnConditionEvaluator.evaluate("courage >= 2", vars));
    assertTrue(VnConditionEvaluator.evaluate("playerName == \"Ari\"", vars));
    assertTrue(VnConditionEvaluator.evaluate("flag && courage > 1", vars));
    assertFalse(VnConditionEvaluator.evaluate("flag && courage > 5", vars));
  }

  @Test
  void supportsParenthesesAndNegation() {
    Map<String, Object> vars = Map.of(
        "a", 3,
        "b", 1,
        "locked", false
    );

    assertTrue(VnConditionEvaluator.evaluate("(a > b) && !locked", vars));
    assertFalse(VnConditionEvaluator.evaluate("not (a > b)", vars));
    assertTrue(VnConditionEvaluator.evaluate("a > 0 or b > 10", vars));
  }

  @Test
  void reportsInvalidExpressions() {
    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> VnConditionEvaluator.validate("score >")
    );
    assertTrue(ex.getMessage().contains("column"));
  }

  @Test
  void arithmeticAdditionAndSubtraction() {
    Map<String, Object> vars = Map.of("coins", 50, "price", 30);
    assertTrue(VnConditionEvaluator.evaluate("coins - price >= 20", vars));
    assertFalse(VnConditionEvaluator.evaluate("coins - price >= 25", vars));
    assertTrue(VnConditionEvaluator.evaluate("coins + 10 == 60", vars));
  }

  @Test
  void arithmeticMultiplicationDivisionModulo() {
    Map<String, Object> vars = Map.of("a", 10, "b", 3);
    assertTrue(VnConditionEvaluator.evaluate("a * b == 30", vars));
    assertTrue(VnConditionEvaluator.evaluate("a / 2 == 5", vars));
    assertTrue(VnConditionEvaluator.evaluate("a % b == 1", vars));
  }

  @Test
  void arithmeticPrecedence() {
    Map<String, Object> vars = Map.of("x", 5);
    // * binds tighter than +
    assertTrue(VnConditionEvaluator.evaluate("x + 2 * 3 == 11", vars));
    // parentheses override precedence
    assertTrue(VnConditionEvaluator.evaluate("(x + 2) * 3 == 21", vars));
  }

  @Test
  void unaryNegation() {
    Map<String, Object> vars = Map.of("val", 7);
    assertTrue(VnConditionEvaluator.evaluate("-val == -7", vars));
    assertTrue(VnConditionEvaluator.evaluate("-val + 10 == 3", vars));
  }

  @Test
  void stringConcatenation() {
    Map<String, Object> vars = Map.of("prefix", "hello", "suffix", "world");
    assertTrue(VnConditionEvaluator.evaluate("prefix + suffix == \"helloworld\"", vars));
    assertTrue(VnConditionEvaluator.evaluate("prefix + \" \" + suffix == \"hello world\"", vars));
  }

  @Test
  void divisionByZeroThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> VnConditionEvaluator.evaluate("10 / 0 > 0", Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> VnConditionEvaluator.evaluate("10 % 0 > 0", Map.of()));
  }

  @Test
  void arithmeticWithComparisonAndLogic() {
    Map<String, Object> vars = Map.of("coins", 100, "price", 25, "hasDiscount", true);
    assertTrue(VnConditionEvaluator.evaluate("coins >= price * 3 && hasDiscount", vars));
    assertFalse(VnConditionEvaluator.evaluate("coins >= price * 5", vars));
    assertTrue(VnConditionEvaluator.evaluate("coins - price * 2 >= 50", vars));
  }
}
