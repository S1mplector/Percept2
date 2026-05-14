package com.jvn.web;

import com.jvn.core.scene2d.Blitter2D;
import com.jvn.render.RendererFactory;
import com.jvn.render.RenderSurface;

/**
 * Factory for creating web-based renderer instances (TeaVM/Canvas 2D).
 */
public class WebRendererFactory implements RendererFactory {

  @Override
  public Blitter2D createBlitter2D(RenderSurface surface) {
    if (surface instanceof WebCanvasRenderSurface) {
      return new WebRenderer(surface);
    }
    throw new IllegalArgumentException("WebRendererFactory requires WebCanvasRenderSurface");
  }

  @Override
  public String getRendererName() {
    return "WebGL/Canvas2D";
  }
}
