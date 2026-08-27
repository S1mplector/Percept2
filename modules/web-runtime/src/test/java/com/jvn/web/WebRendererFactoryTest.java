package com.jvn.web;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.jvn.render.RendererFactory;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderFeature;

/**
 * Tests for {@link WebRendererFactory}.
 */
public class WebRendererFactoryTest {

  @Test
  void testFactoryCreation() {
    RendererFactory factory = new WebRendererFactory();
    assertNotNull(factory);
    assertEquals("Canvas 2D", factory.getRendererName());
    assertTrue(factory.getCapabilities().supports(RenderFeature.BLEND_MODES));
    assertTrue(factory.getCapabilities().supports(RenderFeature.IMAGE_DIMENSIONS));
  }

  @Test
  void testFactoryThrowsOnWrongSurfaceType() {
    RendererFactory factory = new WebRendererFactory();

    // Create a mock surface that's not WebCanvasRenderSurface
    MockSurface surface = new MockSurface();

    assertThrows(IllegalArgumentException.class, () -> {
      factory.createBlitter2D(surface);
    });
  }

  /**
   * Mock RenderSurface that's not a WebCanvasRenderSurface.
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
    WebRendererFactory factory = new WebRendererFactory();
    String name = factory.getRendererName();
    assertTrue(name.contains("Canvas"));
  }
}
