package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}
