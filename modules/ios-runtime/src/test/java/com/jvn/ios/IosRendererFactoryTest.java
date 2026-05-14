package com.jvn.ios;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.jvn.render.RendererFactory;
import com.jvn.core.assets.ClasspathAssetManager;

/**
 * Tests for {@link IosRendererFactory}.
 */
public class IosRendererFactoryTest {

  @Test
  void testFactoryCreation() {
    RendererFactory factory = new IosRendererFactory();
    assertNotNull(factory);
    assertEquals("iOS CoreGraphics", factory.getRendererName());
  }

  @Test
  void testFactoryWithCustomAssetManager() {
    RendererFactory factory = new IosRendererFactory(new ClasspathAssetManager());
    assertNotNull(factory);
    assertEquals("iOS CoreGraphics", factory.getRendererName());
  }

  @Test
  void testFactoryThrowsOnWrongSurfaceType() {
    RendererFactory factory = new IosRendererFactory();

    // Create a mock surface that's not IosRenderSurface
    MockSurface surface = new MockSurface();

    assertThrows(IllegalArgumentException.class, () -> {
      factory.createBlitter2D(surface);
    });
  }

  /**
   * Mock RenderSurface that's not an IosRenderSurface.
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
    IosRendererFactory factory = new IosRendererFactory();
    String name = factory.getRendererName();
    assertTrue(name.contains("iOS") || name.contains("CoreGraphics"));
  }
}
