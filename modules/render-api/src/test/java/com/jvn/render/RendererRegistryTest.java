package com.jvn.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * Tests for {@link RendererRegistry}.
 */
public class RendererRegistryTest {

  @Test
  void testRegistryLoadsAvailableFactories() {
    RendererRegistry registry = new RendererRegistry();
    List<String> renderers = registry.getAvailableRenderers();
    assertNotNull(renderers, "Available renderers list should not be null");
  }

  @Test
  void testGetFirstFactory() {
    RendererRegistry registry = new RendererRegistry();
    RendererFactory factory = registry.getFirst();
    // Factory may be null if no implementations on classpath, or not null if some are available
    // In test context, we expect at least one to be available
    assertNotNull(factory, "At least one renderer factory should be available");
  }

  @Test
  void testIsAvailableCheck() {
    RendererRegistry registry = new RendererRegistry();
    // Check for a renderer that should exist (JavaFX from fx module)
    boolean fxAvailable = registry.isAvailable("JavaFX");
    // May or may not be available depending on module path
    assertTrue(fxAvailable || !registry.getAvailableRenderers().isEmpty(),
        "Registry should have renderers or JavaFX should be available");
  }

  @Test
  void testGetByName() {
    RendererRegistry registry = new RendererRegistry();
    RendererFactory factory = registry.get("JavaFX");
    // May return null if not on classpath
    if (registry.isAvailable("JavaFX")) {
      assertNotNull(factory, "Should retrieve JavaFX factory by name");
      assertEquals("JavaFX", factory.getRendererName(), "Factory should return correct name");
    }
  }

  @Test
  void testGetNonexistentFactory() {
    RendererRegistry registry = new RendererRegistry();
    RendererFactory factory = registry.get("NonExistent");
    assertNull(factory, "Non-existent factory should return null");
  }
}
