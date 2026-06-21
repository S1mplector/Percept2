package com.jvn.android;

import com.jvn.core.assets.AssetManager;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RendererCapabilities;
import com.jvn.render.RendererFactory;
import com.jvn.render.RenderSurface;

/**
 * Factory for creating Android-based renderer instances (Canvas).
 */
public class AndroidRendererFactory implements RendererFactory {

  @Override
  public RendererCapabilities getCapabilities() { return AndroidRenderer.CAPABILITIES; }

  private final AssetManager assetManager;

  /**
   * Create a factory with default asset manager (classpath).
   */
  public AndroidRendererFactory() {
    this(new ClasspathAssetManager());
  }

  /**
   * Create a factory with a custom asset manager.
   *
   * @param assetManager the asset manager to use for image loading
   */
  public AndroidRendererFactory(AssetManager assetManager) {
    this.assetManager = assetManager;
  }

  @Override
  public Blitter2D createBlitter2D(RenderSurface surface) {
    if (surface instanceof AndroidRenderSurface) {
      return new AndroidRenderer(surface, assetManager);
    }
    throw new IllegalArgumentException("AndroidRendererFactory requires AndroidRenderSurface");
  }

  @Override
  public String getRendererName() {
    return "Android Canvas";
  }
}
