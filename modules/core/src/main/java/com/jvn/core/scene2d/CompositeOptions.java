package com.jvn.core.scene2d;

import java.util.Objects;

/** Immutable state applied while an offscreen target is composited. */
public final class CompositeOptions {
  private static final CompositeOptions NORMAL =
      new CompositeOptions(1.0, RenderBlendMode.NORMAL, 0.0, null);

  private final double alpha;
  private final RenderBlendMode blendMode;
  private final double blurRadius;
  private final double[] colorMatrix;

  public CompositeOptions(
      double alpha,
      RenderBlendMode blendMode,
      double blurRadius,
      double[] colorMatrix
  ) {
    if (!Double.isFinite(alpha) || alpha < 0.0 || alpha > 1.0) {
      throw new IllegalArgumentException("alpha must be finite and in [0, 1]");
    }
    if (!Double.isFinite(blurRadius) || blurRadius < 0.0) {
      throw new IllegalArgumentException("blurRadius must be finite and non-negative");
    }
    if (colorMatrix != null && colorMatrix.length != 20) {
      throw new IllegalArgumentException("colorMatrix must contain exactly 20 values");
    }
    this.alpha = alpha;
    this.blendMode = Objects.requireNonNull(blendMode, "blendMode");
    this.blurRadius = blurRadius;
    this.colorMatrix = colorMatrix == null ? null : colorMatrix.clone();
  }

  public static CompositeOptions normal() { return NORMAL; }

  public double alpha() { return alpha; }
  public RenderBlendMode blendMode() { return blendMode; }
  public double blurRadius() { return blurRadius; }
  public double[] colorMatrix() { return colorMatrix == null ? null : colorMatrix.clone(); }

  public CompositeOptions withAlpha(double value) {
    return new CompositeOptions(value, blendMode, blurRadius, colorMatrix);
  }

  public CompositeOptions withBlendMode(RenderBlendMode value) {
    return new CompositeOptions(alpha, value, blurRadius, colorMatrix);
  }

  public CompositeOptions withBlurRadius(double value) {
    return new CompositeOptions(alpha, blendMode, value, colorMatrix);
  }

  public CompositeOptions withColorMatrix(double[] value) {
    return new CompositeOptions(alpha, blendMode, blurRadius, value);
  }
}
