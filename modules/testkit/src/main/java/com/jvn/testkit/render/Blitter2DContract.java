package com.jvn.testkit.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.scene2d.Blitter2D;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared contract checks for {@link Blitter2D} implementations.
 *
 * <p>The smoke contract is suitable for platform renderers that cannot expose
 * pixels in headless tests. The pixel contract is stricter and should be used by
 * backends that can render into an inspectable framebuffer.</p>
 */
public final class Blitter2DContract {
  private Blitter2DContract() {}

  public static void assertSmokeContract(Supplier<? extends Blitter2D> factory) {
    Objects.requireNonNull(factory, "factory");
    Blitter2D b = Objects.requireNonNull(factory.get(), "factory returned null");

    assertDoesNotThrow(() -> {
      b.clear(0.0, 0.0, 0.0, 1.0);
      b.setFill(1.0, 0.0, 0.0, 1.0);
      b.setStroke(0.0, 1.0, 0.0, 1.0);
      b.setStrokeWidth(2.0);
      b.setGlobalAlpha(0.75);
      b.setFont("SansSerif", 16.0, false);
      b.setTextAlign("center", "middle");
      b.setBlendMode("normal");

      b.push();
      b.translate(4.0, 5.0);
      b.rotateDeg(15.0);
      b.scale(1.25, 0.75);
      b.transform(1.0, 0.0, 0.0, 1.0, 2.0, 3.0);
      b.setClipRect(0.0, 0.0, 64.0, 64.0);
      b.fillRect(2.0, 3.0, 10.0, 11.0);
      b.strokeRect(4.0, 5.0, 12.0, 13.0);
      b.fillCircle(20.0, 21.0, 5.0);
      b.strokeCircle(22.0, 23.0, 6.0);
      b.drawLine(0.0, 0.0, 30.0, 30.0);
      b.drawImage("missing-contract-image.png", 1.0, 2.0, 8.0, 9.0);
      b.drawImageRegion("missing-contract-atlas.png", 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0);
      b.drawText("JVN", 16.0, 20.0, 14.0, true);
      b.pop();
    });

    double narrow = b.measureTextWidth("J", 14.0, false);
    double wide = b.measureTextWidth("JVN renderer", 14.0, false);
    assertTrue(narrow >= 0.0, "text measurement must not be negative");
    assertTrue(wide >= narrow, "longer text should not measure narrower than shorter text");
  }

  public static void assertPixelContract(
      Supplier<? extends Blitter2D> factory,
      PixelProbe pixels,
      Runnable resetPixels
  ) {
    Objects.requireNonNull(factory, "factory");
    Objects.requireNonNull(pixels, "pixels");
    Objects.requireNonNull(resetPixels, "resetPixels");

    assertSmokeContract(factory);
    verifiesFillAndClear(factory, pixels, resetPixels);
    verifiesPushPopRestoresTransform(factory, pixels, resetPixels);
    verifiesClipIsRestoredByPop(factory, pixels, resetPixels);
    verifiesGlobalAlpha(factory, pixels, resetPixels);
  }

  private static void verifiesFillAndClear(
      Supplier<? extends Blitter2D> factory,
      PixelProbe pixels,
      Runnable resetPixels
  ) {
    resetPixels.run();
    Blitter2D b = factory.get();
    b.clear(0.0, 0.0, 0.0, 1.0);
    b.setFill(1.0, 0.0, 0.0, 1.0);
    b.fillRect(4.0, 4.0, 12.0, 12.0);

    assertColorNear(255, 0, 0, pixels.rgbaAt(8, 8), "filled rectangle pixel");
    assertColorNear(0, 0, 0, pixels.rgbaAt(24, 24), "clear background pixel");
  }

  private static void verifiesPushPopRestoresTransform(
      Supplier<? extends Blitter2D> factory,
      PixelProbe pixels,
      Runnable resetPixels
  ) {
    resetPixels.run();
    Blitter2D b = factory.get();
    b.clear(0.0, 0.0, 0.0, 1.0);
    b.setFill(0.0, 1.0, 0.0, 1.0);
    b.push();
    b.translate(20.0, 0.0);
    b.fillRect(0.0, 0.0, 10.0, 10.0);
    b.pop();
    b.setFill(0.0, 0.0, 1.0, 1.0);
    b.fillRect(0.0, 0.0, 10.0, 10.0);

    assertColorNear(0, 255, 0, pixels.rgbaAt(24, 4), "translated draw pixel");
    assertColorNear(0, 0, 255, pixels.rgbaAt(4, 4), "post-pop draw pixel");
  }

  private static void verifiesClipIsRestoredByPop(
      Supplier<? extends Blitter2D> factory,
      PixelProbe pixels,
      Runnable resetPixels
  ) {
    resetPixels.run();
    Blitter2D b = factory.get();
    b.clear(0.0, 0.0, 0.0, 1.0);
    b.setFill(1.0, 0.0, 0.0, 1.0);
    b.push();
    b.setClipRect(0.0, 0.0, 8.0, 8.0);
    b.fillRect(0.0, 0.0, 20.0, 20.0);
    b.pop();
    b.setFill(0.0, 0.0, 1.0, 1.0);
    b.fillRect(16.0, 16.0, 8.0, 8.0);

    assertColorNear(255, 0, 0, pixels.rgbaAt(4, 4), "inside clipped fill");
    assertColorNear(0, 0, 255, pixels.rgbaAt(18, 18), "clip restored after pop");
  }

  private static void verifiesGlobalAlpha(
      Supplier<? extends Blitter2D> factory,
      PixelProbe pixels,
      Runnable resetPixels
  ) {
    resetPixels.run();
    Blitter2D b = factory.get();
    b.clear(0.0, 0.0, 0.0, 1.0);
    b.setGlobalAlpha(0.5);
    b.setFill(1.0, 0.0, 0.0, 1.0);
    b.fillRect(0.0, 0.0, 12.0, 12.0);

    Rgba pixel = pixels.rgbaAt(4, 4);
    assertTrue(pixel.r() >= 100 && pixel.r() <= 180, "half-alpha red should blend over black");
    assertTrue(pixel.g() <= 8, "half-alpha fill should not add green");
    assertTrue(pixel.b() <= 8, "half-alpha fill should not add blue");
  }

  public static void assertColorNear(int r, int g, int b, Rgba actual, String label) {
    assertNotNull(actual, label + " was not readable");
    assertEquals((double) r, actual.r(), 8.0, label + " red");
    assertEquals((double) g, actual.g(), 8.0, label + " green");
    assertEquals((double) b, actual.b(), 8.0, label + " blue");
  }

  @FunctionalInterface
  public interface PixelProbe {
    Rgba rgbaAt(int x, int y);
  }

  public record Rgba(int r, int g, int b, int a) {}
}
