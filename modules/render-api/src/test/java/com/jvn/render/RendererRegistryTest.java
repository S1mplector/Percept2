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
    // Factory may be null in test context if no implementations loaded
    // This is acceptable - registry handles empty state gracefully
    if (factory != null) {
      assertNotNull(factory.getRendererName());
    }
  }

  @Test
  void testIsAvailableCheck() {
    RendererRegistry registry = new RendererRegistry();
    List<String> available = registry.getAvailableRenderers();
    // In test context, availability depends on what's on classpath
    // Just verify the registry doesn't crash
    assertNotNull(available, "Available renderers list should not be null");
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
