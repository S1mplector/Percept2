package com.jvn.core.nativebridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeSearchBridgeTest {

  @Test
  void findCaseInsensitiveMatches() {
    assertEquals(0, NativeSearchBridge.findCaseInsensitive("Hello World", "hello"));
    assertEquals(6, NativeSearchBridge.findCaseInsensitive("Hello World", "world"));
    assertEquals(-1, NativeSearchBridge.findCaseInsensitive("Hello World", "xyz"));
  }

  @Test
  void countCaseInsensitiveSupportsOverlap() {
    assertEquals(3, NativeSearchBridge.countCaseInsensitive("aaaa", "aa"));
    assertEquals(0, NativeSearchBridge.countCaseInsensitive("abc", "zzz"));
  }

  @Test
  void findAllCaseInsensitiveReturnsPositions() {
    int[] positions = NativeSearchBridge.findAllCaseInsensitive("Hello hello heLLo world", "hello");
    assertArrayEquals(new int[] {0, 6, 12}, positions);

    int[] limited = NativeSearchBridge.findAllCaseInsensitive("Hello hello heLLo world", "hello", 2);
    assertArrayEquals(new int[] {0, 6}, limited);
  }

  @Test
  void nonAsciiFallsBackToJavaPath() {
    int[] positions = NativeSearchBridge.findAllCaseInsensitive("naïve café", "NAÏ");
    assertArrayEquals(new int[] {0}, positions);
  }
}

