package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class InspectorViewTest {

  @Test
  void acceptsOnlyFiniteNumericInput() {
    OptionalDouble parsed = InspectorView.parseFiniteDouble(" 12.5 ");
    assertTrue(parsed.isPresent());
    assertEquals(12.5, parsed.getAsDouble());
    assertTrue(InspectorView.parseFiniteDouble("NaN").isEmpty());
    assertTrue(InspectorView.parseFiniteDouble("Infinity").isEmpty());
    assertTrue(InspectorView.parseFiniteDouble("not-a-number").isEmpty());
    assertTrue(InspectorView.parseFiniteDouble(" ").isEmpty());
  }
}
