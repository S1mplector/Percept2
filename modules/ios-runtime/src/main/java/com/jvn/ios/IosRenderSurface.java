package com.jvn.ios;

import com.jvn.render.RenderSurface;

/**
 * {@code RenderSurface} implementation for iOS using UIView and CoreGraphics.
 *
 * <p>This surface wraps an iOS {@code UIView} and provides access to its graphics context
 * for rendering with CoreGraphics or Metal.</p>
 */
public class IosRenderSurface implements RenderSurface {

  private final Object window; // UIWindow
  private final Object view; // UIView
  private double cachedWidth;
  private double cachedHeight;
  private double pixelScale;
  private boolean valid = true;

  /**
   * Construct an iOS render surface.
   *
   * @param window iOS UIWindow
   */
  public IosRenderSurface(Object window) {
    this.window = window;
    this.view = getMainView(window);
    this.cachedWidth = getViewWidth(view);
    this.cachedHeight = getViewHeight(view);
    this.pixelScale = getScreenScale();
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
    // iOS rendering is handled by the view's drawing cycle and CADisplayLink
  }

  @Override
  public boolean isValid() {
    return valid && view != null;
  }

  @Override
  public void dispose() {
    valid = false;
  }

  /**
   * Get the underlying UIView.
   */
  public Object getView() {
    return view;
  }

  // iOS interop methods (via Multi-OS Engine reflection bindings)
  private static native Object getMainView(Object window) /*-{
    // Would use MOE reflection to call [window rootViewController].view
    return null;
  }-*/;

  private static native double getViewWidth(Object view) /*-{
    // Would use MOE reflection to get view.bounds.size.width
    return 1280.0;
  }-*/;

  private static native double getViewHeight(Object view) /*-{
    // Would use MOE reflection to get view.bounds.size.height
    return 720.0;
  }-*/;

  private static native double getScreenScale() /*-{
    // Would use MOE reflection to get [UIScreen mainScreen].scale
    // Typically 1.0 (iPhone), 2.0 (Retina), 3.0 (Plus), etc.
    return 1.0;
  }-*/;
}
