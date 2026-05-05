package com.jvn.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JvnAppCliValueTest {

  @Test
  void cleanCliValuePreservesLegitimateTrailingSpacesInPaths() {
    assertEquals(
        "/Users/example/JVN Projects/Was_I_Write (JVN) ",
        JvnApp.cleanCliValue("/Users/example/JVN Projects/Was_I_Write (JVN) "));
  }

  @Test
  void cleanCliValueStripsOnlyWrappingQuotes() {
    assertEquals(
        "/Users/example/JVN Projects/Was_I_Write (JVN) ",
        JvnApp.cleanCliValue("\"/Users/example/JVN Projects/Was_I_Write (JVN) \""));
  }

  @Test
  void cleanCliValueAllowsWhitespaceOutsideQuotedValues() {
    assertEquals(
        "/Users/example/JVN Projects/Was_I_Write (JVN) ",
        JvnApp.cleanCliValue("  '/Users/example/JVN Projects/Was_I_Write (JVN) '  "));
  }
}
