package com.jvn.fx.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.junit.jupiter.api.Test;

class ProjectFontResolverTest {

  @Test
  void reusesResolvedSystemFontsForIdenticalRequests() {
    ProjectFontResolver.clearCache();

    Font first = ProjectFontResolver.resolve(null, "SansSerif", FontWeight.BOLD, 18.0, "SansSerif");
    Font second = ProjectFontResolver.resolve(null, "SansSerif", FontWeight.BOLD, 18.0, "SansSerif");
    Font differentWeight = ProjectFontResolver.resolve(null, "SansSerif", FontWeight.NORMAL, 18.0, "SansSerif");

    assertNotNull(first);
    assertSame(first, second);
    assertNotSame(first, differentWeight);
  }
}
