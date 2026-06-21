package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Portable helper for creating, rendering, and combining offscreen 2D layers. */
public final class Compositor2D implements AutoCloseable {
  private final Blitter2D destination;
  private final List<RenderTarget2D> ownedTargets = new ArrayList<>();

  public Compositor2D(Blitter2D destination) {
    this.destination = Objects.requireNonNull(destination, "destination");
    destination.require(RenderFeature.OFFSCREEN_RENDER_TARGETS);
  }

  public RenderTarget2D createTarget(double width, double height) {
    return createTarget(width, height, 1.0);
  }

  public RenderTarget2D createTarget(double width, double height, double pixelScale) {
    RenderTarget2D target = destination.createRenderTarget(width, height, pixelScale);
    ownedTargets.add(target);
    return target;
  }

  public RenderTarget2D renderToTarget(
      double width,
      double height,
      double pixelScale,
      Consumer<Blitter2D> renderer
  ) {
    Objects.requireNonNull(renderer, "renderer");
    RenderTarget2D target = createTarget(width, height, pixelScale);
    renderer.accept(target.getBlitter());
    return target;
  }

  public void composite(RenderTarget2D target, double x, double y, double width, double height) {
    composite(target, x, y, width, height, CompositeOptions.normal());
  }

  public void composite(
      RenderTarget2D target,
      double x,
      double y,
      double width,
      double height,
      CompositeOptions options
  ) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(options, "options");
    ensureValid(target);
    validateOptions(options);

    destination.push();
    try {
      destination.setGlobalAlpha(options.alpha());
      if (options.blendMode() != RenderBlendMode.NORMAL) {
        destination.setBlendMode(options.blendMode());
      }
      if (options.blurRadius() > 0.0) destination.setBlurRadius(options.blurRadius());
      double[] matrix = options.colorMatrix();
      if (matrix != null) destination.setColorMatrix(matrix);
      destination.drawRenderTarget(target, x, y, width, height);
    } finally {
      destination.pop();
    }
  }

  /** Draw the first target, then fade the second target over it. */
  public void crossFade(
      RenderTarget2D from,
      RenderTarget2D to,
      double progress,
      double x,
      double y,
      double width,
      double height
  ) {
    double t = Math.max(0.0, Math.min(1.0, progress));
    if (t < 1.0) composite(from, x, y, width, height);
    if (t > 0.0) composite(to, x, y, width, height, CompositeOptions.normal().withAlpha(t));
  }

  /**
   * Multiply {@code content}'s alpha by {@code mask}'s alpha in place. Both
   * targets must belong to the same backend and remain valid.
   */
  public void applyAlphaMask(RenderTarget2D content, RenderTarget2D mask) {
    ensureValid(content);
    ensureValid(mask);
    Blitter2D targetBlitter = content.getBlitter();
    targetBlitter.require(RenderFeature.ALPHA_MASKS);
    targetBlitter.applyAlphaMask(mask);
  }

  private void validateOptions(CompositeOptions options) {
    RendererCapabilities capabilities = destination.getCapabilities();
    capabilities.requireBlendMode(options.blendMode());
    if (options.blurRadius() > 0.0) capabilities.require(RenderFeature.BLUR);
    if (options.colorMatrix() != null) capabilities.require(RenderFeature.COLOR_MATRIX);
  }

  private static void ensureValid(RenderTarget2D target) {
    if (!target.isValid()) throw new IllegalStateException("Render target has been disposed");
  }

  @Override
  public void close() {
    for (int i = ownedTargets.size() - 1; i >= 0; i--) {
      RenderTarget2D target = ownedTargets.get(i);
      if (target.isValid()) target.dispose();
    }
    ownedTargets.clear();
  }
}
