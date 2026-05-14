package com.jvn.ios;

import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.render.RendererFactory;
import com.jvn.render.RenderSurface;

/**
 * Factory for creating iOS-based renderer instances (CoreGraphics).
 */
public class IosRendererFactory implements RendererFactory {

  private final AssetManager assetManager;

  /**
   * Create a factory with default asset manager (classpath).
   */
  public IosRendererFactory() {
    this(new ClasspathAssetManager());
  }

  /**
   * Create a factory with a custom asset manager.
   *
   * @param assetManager the asset manager to use for image loading
   */
  public IosRendererFactory(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  @Override
  public Blitter2D createBlitter2D(RenderSurface surface) {
    if (surface instanceof IosRenderSurface) {
      return new IosRenderer(surface, assetManager);
    }
    throw new IllegalArgumentException("IosRendererFactory requires IosRenderSurface");
  }

  @Override
  public String getRendererName() {
    return "iOS CoreGraphics";
  }
}
