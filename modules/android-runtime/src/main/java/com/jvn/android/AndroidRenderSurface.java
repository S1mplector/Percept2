package com.jvn.android;

import com.jvn.render.RenderSurface;

/**
 * {@code RenderSurface} implementation for Android using SurfaceView and Canvas.
 *
 * <p>This surface wraps an Android {@code android.view.SurfaceView} and provides
 * access to its {@code SurfaceHolder} for drawing with {@code Canvas}.</p>
 */
public class AndroidRenderSurface implements RenderSurface {

  private final Object context; // android.content.Context
  private final Object surfaceView; // android.view.SurfaceView
  private final Object surfaceHolder; // android.view.SurfaceHolder
  private double cachedWidth;
  private double cachedHeight;
  private double pixelScale = 1.0;
  private boolean valid = true;

  /**
   * Construct an Android render surface.
   *
   * @param context Android application context
   */
  public AndroidRenderSurface(Object context) {
    this.context = context;
    this.surfaceView = createSurfaceView(context);
    this.surfaceHolder = getSurfaceHolder(surfaceView);
    this.cachedWidth = getDisplayWidth(context);
    this.cachedHeight = getDisplayHeight(context);
    this.pixelScale = getDevicePixelRatio(context);
  }

  @Override
  public double getWidth() {
    return cachedWidth;
  }

  @Override
  public double getHeight() {
    return cachedHeight;
  }

  @Override
  public double getPixelScale() {
    return pixelScale;
  }

  @Override
  public void present() {
    // Canvas drawing is done directly; surface compositor handles presentation
  }

  @Override
  public boolean isValid() {
    return valid && surfaceHolder != null;
  }

  @Override
  public void dispose() {
    valid = false;
  }

  /**
   * Get the underlying SurfaceHolder (for native Android rendering).
   */
  public Object getSurfaceHolder() {
    return surfaceHolder;
  }

  /**
   * Get the underlying SurfaceView.
   */
  public Object getSurfaceView() {
    return surfaceView;
  }

  // Android interop methods (placeholders for JNI/reflection-based access)
  private static native Object createSurfaceView(Object context) /*-{
    // Would use reflection to create android.view.SurfaceView
    return null;
  }-*/;

  private static native Object getSurfaceHolder(Object surfaceView) /*-{
    // Would use reflection to call getHolder() on SurfaceView
    return null;
  }-*/;

  private static native double getDisplayWidth(Object context) /*-{
    // Would use reflection to get display metrics
    return 1280.0;
  }-*/;

  private static native double getDisplayHeight(Object context) /*-{
    // Would use reflection to get display metrics
    return 720.0;
  }-*/;

  private static native double getDevicePixelRatio(Object context) /*-{
    // Would use reflection to get device density
    return 1.0;
  }-*/;
}
