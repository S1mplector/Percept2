package com.jvn.android;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.jvn.render.RendererFactory;
import com.jvn.core.assets.ClasspathAssetManager;

/**
 * Tests for {@link AndroidRendererFactory}.
 */
public class AndroidRendererFactoryTest {

  @Test
  void testFactoryCreation() {
    RendererFactory factory = new AndroidRendererFactory();
    assertNotNull(factory);
    assertEquals("Android Canvas", factory.getRendererName());
  }

  @Test
  void testFactoryWithCustomAssetManager() {
    RendererFactory factory = new AndroidRendererFactory(new ClasspathAssetManager());
    assertNotNull(factory);
    assertEquals("Android Canvas", factory.getRendererName());
  }

  @Test
  void testFactoryThrowsOnWrongSurfaceType() {
    RendererFactory factory = new AndroidRendererFactory();

    // Create a mock surface that's not AndroidRenderSurface
    MockSurface surface = new MockSurface();

    assertThrows(IllegalArgumentException.class, () -> {
      factory.createBlitter2D(surface);
    });
  }

  /**
   * Mock RenderSurface that's not an AndroidRenderSurface.
   */
  static class MockSurface implements com.jvn.render.RenderSurface {
    @Override
    public double getWidth() { return 800; }
    @Override
    public double getHeight() { return 600; }
    @Override
    public double getPixelScale() { return 1.0; }
    @Override
    public void present() {}
    @Override
    public boolean isValid() { return true; }
    @Override
    public void dispose() {}
  }

  @Test
  void testRendererNameCorrect() {
    AndroidRendererFactory factory = new AndroidRendererFactory();
    String name = factory.getRendererName();
    assertTrue(name.contains("Android") || name.contains("Canvas"));
  }
}
