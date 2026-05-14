package com.jvn.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for RenderSurface implementations.
 */
public class RenderSurfaceIntegrationTest {

  /**
   * Mock RenderSurface for testing.
   */
  static class MockRenderSurface implements RenderSurface {
    private double width = 800;
    private double height = 600;
    private double pixelScale = 1.0;
    private boolean valid = true;

    @Override
    public double getWidth() {
      return width;
    }

    @Override
    public double getHeight() {
      return height;
    }

    @Override
    public double getPixelScale() {
      return pixelScale;
    }

    @Override
    public void present() {
      // Mock present
    }

    @Override
    public boolean isValid() {
      return valid;
    }

    @Override
    public void dispose() {
      valid = false;
    }
  }

  @Test
  void testRenderSurfaceCreation() {
    RenderSurface surface = new MockRenderSurface();
    assertEquals(800, surface.getWidth());
    assertEquals(600, surface.getHeight());
    assertEquals(1.0, surface.getPixelScale());
    assertTrue(surface.isValid());
  }

  @Test
  void testRenderSurfaceDispose() {
    RenderSurface surface = new MockRenderSurface();
    assertTrue(surface.isValid());
    surface.dispose();
    assertFalse(surface.isValid());
  }

  @Test
  void testRenderSurfacePresent() {
    RenderSurface surface = new MockRenderSurface();
    surface.present();  // Should not throw
  }

  @Test
  void testRenderSurfaceDimensions() {
    RenderSurface surface = new MockRenderSurface();
    double expectedArea = surface.getWidth() * surface.getHeight();
    assertEquals(480000, expectedArea);
  }
}
