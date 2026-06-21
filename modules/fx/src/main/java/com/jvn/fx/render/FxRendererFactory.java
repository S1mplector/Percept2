package com.jvn.fx.render;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.render.RendererFactory;
import com.jvn.render.RenderSurface;

/**
 * Factory for creating JavaFX-based renderer instances.
 */
public class FxRendererFactory implements RendererFactory {

  @Override
  public RendererCapabilities getCapabilities() {
    return FxBlitter2D.CAPABILITIES;
  }

  @Override
  public Blitter2D createBlitter2D(RenderSurface surface) {
    if (surface instanceof FxRenderSurface fxSurface) {
      FxBlitter2D blitter = new FxBlitter2D(fxSurface.getGraphicsContext());
      blitter.setViewport(fxSurface.getWidth(), fxSurface.getHeight());
      return blitter;
    }
    throw new IllegalArgumentException("FxRendererFactory requires FxRenderSurface");
  }

  @Override
  public String getRendererName() {
    return "JavaFX";
  }
}
