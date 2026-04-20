package com.jvn.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EditorAppStartupPathTest {

  @Test
  void cleanStartupPathValuePreservesTrailingSpaceInProjectDirectoryName() {
    assertEquals(
        "/Users/example/JVN Projects/Was_I_Write (JVN) ",
        EditorApp.cleanStartupPathValue("/Users/example/JVN Projects/Was_I_Write (JVN) "));
  }

  @Test
  void cleanStartupPathValueStripsWrappingQuotesOnly() {
    assertEquals(
        "/Users/example/JVN Projects/Was_I_Write (JVN) ",
        EditorApp.cleanStartupPathValue("\"/Users/example/JVN Projects/Was_I_Write (JVN) \""));
  }

  @Test
  void cleanStartupPathValueAllowsWhitespaceOutsideQuotedValues() {
    assertEquals(
        "/Users/example/JVN Projects/Was_I_Write (JVN) ",
        EditorApp.cleanStartupPathValue("  '/Users/example/JVN Projects/Was_I_Write (JVN) '  "));
  }
}
