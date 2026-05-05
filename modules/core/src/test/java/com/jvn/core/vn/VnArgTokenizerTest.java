package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VnArgTokenizerTest {
  @Test
  void tokenizesQuotedAndEscapedValues() {
    String input = "jes push game/minigame.jes with title=\"Final Battle\" path=foo\\ bar quote=\\\"wow\\\"";
    List<String> tokens = VnArgTokenizer.tokenize(input);

    assertEquals(List.of(
      "jes",
      "push",
      "game/minigame.jes",
      "with",
      "title=Final Battle",
      "path=foo bar",
      "quote=\"wow\""
    ), tokens);
  }

  @Test
  void supportsUnclosedQuotesByTakingRemainder() {
    String input = "java com.acme.Log#info \"hello world";
    List<String> tokens = VnArgTokenizer.tokenize(input);

    assertEquals(List.of("java", "com.acme.Log#info", "hello world"), tokens);
  }
}
