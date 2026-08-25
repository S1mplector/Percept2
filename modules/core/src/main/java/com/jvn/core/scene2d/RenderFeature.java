package com.jvn.core.scene2d;

/** Optional rendering features that a {@link Blitter2D} backend may provide. */
public enum RenderFeature {
  AFFINE_TRANSFORM,
  VECTOR_PATHS,
  ADVANCED_STROKE,
  RECTANGULAR_CLIP,
  POLYGONS,
  ARCS,
  LINEAR_GRADIENT,
  RADIAL_GRADIENT,
  TEXT_ALIGNMENT,
  BLEND_MODES,
  COLOR_MATRIX,
  BLUR,
  OFFSCREEN_RENDER_TARGETS,
  ALPHA_MASKS,
  TEXT_LAYOUT,
  /** Whole-buffer packed-ARGB pixel read/write on a {@link RenderTarget2D}. */
  PIXEL_ACCESS
}
