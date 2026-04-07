package com.jvn.core.graphics;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.jvn.core.math.Rect;
import com.jvn.core.math.Scalars;
import com.jvn.core.scene2d.AnimatablePropertyRegistry;

/**
 * 2D camera with smooth follow, zoom, and optional world-space bounds clamping.
 *
 * <p>The camera stores the <b>top-left world coordinate</b> of the visible view
 * in {@link #getX()} / {@link #getY()}, not the view centre. Use
 * {@link #setCenter(double, double, double, double)} or
 * {@link #setTargetCenter(double, double, double, double)} when follow logic is
 * authored in centre coordinates.</p>
 *
 * <p>The camera maintains a <b>current position</b> and a <b>target position</b>.
 * Each frame, {@link #update(long)} moves the current position toward the target
 * using an exponential-decay smoothing function, producing silky camera follow
 * without overshoot.</p>
 *
 * <h2>Smoothing Model</h2>
 * <p>Smoothing uses the formula {@code alpha = 1 - e^(-dt / tau)} where {@code tau}
 * is {@link #setSmoothingMs(double)}. Setting {@code tau = 0} makes the camera
 * snap instantly to the target. Larger values produce slower, heavier follow.</p>
 *
 * <h2>Bounds</h2>
 * <p>Optional axis-aligned bounds can be set via {@link #setBounds(double, double, double, double)}.
 * When a logical viewport size is known via {@link #setViewportSize(double, double)},
 * bounds clamping applies to the <em>entire visible view</em>, not only the camera's
 * top-left coordinate. This keeps the rendered frame inside the world edges.</p>
 *
 * <h2>Coordinate Transforms</h2>
 * <p>{@link #worldToScreenX(double)} / {@link #worldToScreenY(double)} and
 * {@link #screenToWorldX(double)} / {@link #screenToWorldY(double)} convert
 * between world-space coordinates and screen-space coordinates relative to the
 * view origin. Overloads with explicit origins are available when the camera is
 * rendered into an inset viewport.</p>
 *
 * @see ViewportScaler2D
 */
public class Camera2D {
  private static final AnimatablePropertyRegistry<Camera2D> PROPERTY_REGISTRY =
      new AnimatablePropertyRegistry<>();

  static {
    registerAnimatableProperty("dof.focus", 0.0, Camera2D::getFocusDepth, Camera2D::setFocusDepth);
    registerAnimatableProperty("dof.strength", 0.0, Camera2D::getDepthOfFieldStrength, Camera2D::setDepthOfFieldStrength);
    registerAnimatableProperty("dof.maxBlur", 0.0, Camera2D::getDepthOfFieldMaxBlur, Camera2D::setDepthOfFieldMaxBlur);
  }

  /** Current camera X position in world space. */
  private double x;

  /** Current camera Y position in world space. */
  private double y;

  /** Zoom factor. Values > 1 zoom in; values in (0, 1) zoom out. */
  private double zoom = 1.0;

  /** Target X the camera is smoothing toward. */
  private double targetX;

  /** Target Y the camera is smoothing toward. */
  private double targetY;

  /**
   * Exponential smoothing time constant (ms).
   * 0 = instant snap; higher = slower follow.
   */
  private double smoothingMs = 0.0;

  /** Whether world-space bounds clamping is active. */
  private boolean hasBounds = false;

  /** Minimum allowed X (left edge of world bounds). */
  private double boundLeft;

  /** Minimum allowed Y (top edge of world bounds). */
  private double boundTop;

  /** Maximum allowed X (right edge of world bounds). */
  private double boundRight;

  /** Maximum allowed Y (bottom edge of world bounds). */
  private double boundBottom;

  /** Logical viewport width used for view-size-aware clamping and centre helpers. */
  private double viewportWidth;

  /** Logical viewport height used for view-size-aware clamping and centre helpers. */
  private double viewportHeight;

  /** Shared depth-of-field focus plane used by scene renderers. */
  private double focusDepth = 0.0;

  /** Blur multiplier applied from depth distance to blur radius. */
  private double depthOfFieldStrength = 0.0;

  /** Maximum blur radius contributed by the DOF system. */
  private double depthOfFieldMaxBlur = 0.0;

  /** Arbitrary numeric camera properties without registered handlers. */
  private final Map<String, Double> customProperties = new LinkedHashMap<>();

  // ──────────────────────────────────────────────────────────────────────────
  //  Position & zoom accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return current camera X in world space */
  public double getX() { return x; }

  /** @return current camera Y in world space */
  public double getY() { return y; }

  /** @return current zoom factor (always > 0) */
  public double getZoom() { return zoom; }

  public double getFocusDepth() { return focusDepth; }
  public double getDepthOfFieldStrength() { return depthOfFieldStrength; }
  public double getDepthOfFieldMaxBlur() { return depthOfFieldMaxBlur; }

  public boolean hasDepthOfField() {
    return depthOfFieldStrength > 1e-9 && depthOfFieldMaxBlur > 1e-9;
  }

  public Map<String, Double> getCustomPropertiesView() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(customProperties));
  }

  /**
   * Teleport the camera (and target) to an exact position immediately.
   * The position is clamped to bounds if enabled.
   *
   * @param x world X
   * @param y world Y
   */
  public void setPosition(double x, double y) {
    this.x = x;
    this.y = y;
    this.targetX = x;
    this.targetY = y;
    clampToBounds();
  }

  /**
   * Set the zoom factor. Values ≤ 0 are clamped to a tiny positive epsilon
   * to prevent division-by-zero in coordinate transforms.
   *
   * @param z desired zoom factor
   */
  public void setZoom(double z) {
    this.zoom = z <= 0 ? 0.0001 : z;
    clampToBounds();
  }

  public void setFocusDepth(double focusDepth) {
    this.focusDepth = sanitizeFinite(focusDepth, 0.0);
  }

  public void setDepthOfFieldStrength(double depthOfFieldStrength) {
    this.depthOfFieldStrength = Math.max(0.0, sanitizeFinite(depthOfFieldStrength, 0.0));
  }

  public void setDepthOfFieldMaxBlur(double depthOfFieldMaxBlur) {
    this.depthOfFieldMaxBlur = Math.max(0.0, sanitizeFinite(depthOfFieldMaxBlur, 0.0));
  }

  public void setCustomProperty(String key, double value) {
    if (key == null || key.isBlank()) return;
    customProperties.put(key.trim(), sanitizeFinite(value, 0.0));
  }

  public double getCustomProperty(String key) {
    if (key == null || key.isBlank()) return 0.0;
    return customProperties.getOrDefault(key.trim(), 0.0);
  }

  public void clearCustomProperties() {
    customProperties.clear();
  }

  public void applyCustomProperty(String key, double value) {
    if (key == null || key.isBlank()) return;
    AnimatablePropertyRegistry.Definition<Camera2D> definition = PROPERTY_REGISTRY.get(key);
    if (definition != null) {
      definition.setValue(this, value);
    } else {
      setCustomProperty(key, value);
    }
  }

  public double readCustomProperty(String key) {
    if (key == null || key.isBlank()) return 0.0;
    AnimatablePropertyRegistry.Definition<Camera2D> definition = PROPERTY_REGISTRY.get(key);
    if (definition != null) return definition.getValue(this);
    return getCustomProperty(key);
  }

  public static AnimatablePropertyRegistry.Definition<Camera2D> registerAnimatableProperty(
      String key,
      double defaultValue,
      java.util.function.ToDoubleFunction<Camera2D> getter,
      java.util.function.ObjDoubleConsumer<Camera2D> setter
  ) {
    return PROPERTY_REGISTRY.register(key, defaultValue, getter, setter);
  }

  public static AnimatablePropertyRegistry.Definition<Camera2D> getAnimatableProperty(String key) {
    return PROPERTY_REGISTRY.get(key);
  }

  public static Collection<AnimatablePropertyRegistry.Definition<Camera2D>> animatableProperties() {
    return PROPERTY_REGISTRY.definitions();
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Smooth follow
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set the target position the camera will smoothly move toward.
   * Call this each frame (e.g. with the player's position) for smooth follow.
   *
   * @param x target world X
   * @param y target world Y
   */
  public void setTarget(double x, double y) {
    this.targetX = x;
    this.targetY = y;
    clampToBounds();
  }

  /** @return the target X the camera is moving toward */
  public double getTargetX() { return targetX; }

  /** @return the target Y the camera is moving toward */
  public double getTargetY() { return targetY; }

  /**
   * Supply the logical viewport size used by this camera.
   *
   * <p>When set, bounds clamping takes the visible view size into account so the
   * full camera frame stays inside the configured world bounds.</p>
   */
  public void setViewportSize(double width, double height) {
    this.viewportWidth = width > 0 ? width : 0.0;
    this.viewportHeight = height > 0 ? height : 0.0;
    clampToBounds();
  }

  /** @return logical viewport width previously supplied via {@link #setViewportSize(double, double)} */
  public double getViewportWidth() { return viewportWidth; }

  /** @return logical viewport height previously supplied via {@link #setViewportSize(double, double)} */
  public double getViewportHeight() { return viewportHeight; }

  /** @return visible world-space width using the known viewport size, or 0 when unknown */
  public double viewWidth() { return viewWidth(viewportWidth); }

  /** @return visible world-space height using the known viewport size, or 0 when unknown */
  public double viewHeight() { return viewHeight(viewportHeight); }

  /**
   * Compute the visible world-space width for a logical viewport width.
   */
  public double viewWidth(double viewportWidth) {
    double width = resolveViewportWidth(viewportWidth);
    return width <= 0 ? 0.0 : width / zoom;
  }

  /**
   * Compute the visible world-space height for a logical viewport height.
   */
  public double viewHeight(double viewportHeight) {
    double height = resolveViewportHeight(viewportHeight);
    return height <= 0 ? 0.0 : height / zoom;
  }

  /** @return current view centre X using the known viewport size, or {@link #getX()} when unknown */
  public double centerX() { return centerX(viewportWidth); }

  /** @return current view centre Y using the known viewport size, or {@link #getY()} when unknown */
  public double centerY() { return centerY(viewportHeight); }

  /**
   * Compute the current view centre X for a logical viewport width.
   */
  public double centerX(double viewportWidth) {
    return x + viewWidth(viewportWidth) * 0.5;
  }

  /**
   * Compute the current view centre Y for a logical viewport height.
   */
  public double centerY(double viewportHeight) {
    return y + viewHeight(viewportHeight) * 0.5;
  }

  /**
   * Teleport the camera so the visible view is centred on the given world point.
   */
  public void setCenter(double centerX, double centerY, double viewportWidth, double viewportHeight) {
    double viewW = viewWidth(viewportWidth);
    double viewH = viewHeight(viewportHeight);
    setPosition(centerX - viewW * 0.5, centerY - viewH * 0.5);
  }

  /**
   * Set the smooth-follow target so the visible view centres on the given world point.
   */
  public void setTargetCenter(double centerX, double centerY, double viewportWidth, double viewportHeight) {
    double viewW = viewWidth(viewportWidth);
    double viewH = viewHeight(viewportHeight);
    setTarget(centerX - viewW * 0.5, centerY - viewH * 0.5);
  }

  /**
   * Set the smoothing time constant.
   *
   * @param ms time constant in milliseconds; 0 = instant snap, 200 = gentle follow
   */
  public void setSmoothingMs(double ms) { this.smoothingMs = ms < 0 ? 0 : ms; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Bounds
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set axis-aligned world-space bounds for the camera. The camera position
   * will be clamped within these bounds after every update or teleport.
   * Arguments are automatically min/max-normalised so order does not matter.
   *
   * @param left   one horizontal edge
   * @param top    one vertical edge
   * @param right  the other horizontal edge
   * @param bottom the other vertical edge
   */
  public void setBounds(double left, double top, double right, double bottom) {
    this.boundLeft = Math.min(left, right);
    this.boundTop = Math.min(top, bottom);
    this.boundRight = Math.max(left, right);
    this.boundBottom = Math.max(top, bottom);
    this.hasBounds = true;
    clampToBounds();
  }

  /**
   * Set axis-aligned world-space bounds from a rectangle.
   */
  public void setBounds(Rect bounds) {
    if (bounds == null) {
      clearBounds();
      return;
    }
    setBounds(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
  }

  /** @return whether world-space clamping is active */
  public boolean hasBounds() { return hasBounds; }

  /**
   * Copy the configured bounds into {@code out}.
   *
   * @return the populated rect, or {@code null} when bounds are disabled
   */
  public Rect bounds(Rect out) {
    if (!hasBounds) return null;
    Rect result = out == null ? new Rect() : out;
    result.x = boundLeft;
    result.y = boundTop;
    result.w = boundRight - boundLeft;
    result.h = boundBottom - boundTop;
    return result;
  }

  /** Remove world-space bounds; the camera may move freely. */
  public void clearBounds() { this.hasBounds = false; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Per-frame update
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Advance the camera toward its target position using exponential smoothing.
   *
   * <p>When {@code smoothingMs ≤ 0} the camera snaps to the target instantly.
   * Otherwise, each axis is interpolated via {@code pos += (target - pos) * (1 - e^(-dt/tau))},
   * producing frame-rate-independent, overshoot-free easing.</p>
   *
   * @param deltaMs elapsed time since the last frame (ms)
   */
  public void update(long deltaMs) {
    if (smoothingMs <= 0) {
      this.x = targetX;
      this.y = targetY;
      clampToBounds();
      return;
    }
    double dt = deltaMs <= 0 ? 0 : deltaMs;
    double tau = smoothingMs;
    double alpha = 1.0 - Math.exp(-dt / tau);
    this.x = this.x + (targetX - this.x) * alpha;
    this.y = this.y + (targetY - this.y) * alpha;
    clampToBounds();
  }

  /** Clamp the current position to the configured world bounds (no-op if bounds are disabled). */
  private void clampToBounds() {
    if (!hasBounds) return;
    double viewW = viewWidth();
    double viewH = viewHeight();
    x = clampAxisToBounds(x, boundLeft, boundRight, viewW);
    y = clampAxisToBounds(y, boundTop, boundBottom, viewH);
    targetX = clampAxisToBounds(targetX, boundLeft, boundRight, viewW);
    targetY = clampAxisToBounds(targetY, boundTop, boundBottom, viewH);
  }

  private double clampAxisToBounds(double value, double min, double max, double visibleSize) {
    if (visibleSize <= 0.0) {
      return Scalars.clamp(value, min, max);
    }
    double available = max - min;
    if (visibleSize >= available) {
      return min + (available - visibleSize) * 0.5;
    }
    return Scalars.clamp(value, min, max - visibleSize);
  }

  private double resolveViewportWidth(double override) {
    return override > 0 ? override : viewportWidth;
  }

  private double resolveViewportHeight(double override) {
    return override > 0 ? override : viewportHeight;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Coordinate transforms
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Convert a world-space X coordinate to screen-space pixels.
   *
   * @param wx world X coordinate
   * @return screen X in pixels
   */
  public double worldToScreenX(double wx) {
    return (wx - x) * zoom;
  }

  /**
   * Convert a world-space X coordinate to screen-space pixels relative to an
   * explicit screen origin.
   */
  public double worldToScreenX(double wx, double originX) {
    return worldToScreenX(wx) + originX;
  }

  /**
   * Convert a world-space X coordinate to screen-space pixels.
   *
   * @param wx            world X coordinate
   * @param viewportWidth viewport width in pixels (ignored; kept for compatibility)
   * @param originX       screen X of the camera origin
   * @return screen X in pixels
   */
  public double worldToScreenX(double wx, double viewportWidth, double originX) {
    return worldToScreenX(wx, originX);
  }

  /**
   * Convert a world-space Y coordinate to screen-space pixels.
   *
   * @param wy world Y coordinate
   * @return screen Y in pixels
   */
  public double worldToScreenY(double wy) {
    return (wy - y) * zoom;
  }

  /**
   * Convert a world-space Y coordinate to screen-space pixels relative to an
   * explicit screen origin.
   */
  public double worldToScreenY(double wy, double originY) {
    return worldToScreenY(wy) + originY;
  }

  /**
   * Convert a world-space Y coordinate to screen-space pixels.
   *
   * @param wy             world Y coordinate
   * @param viewportHeight viewport height in pixels (ignored; kept for compatibility)
   * @param originY        screen Y of the camera origin
   * @return screen Y in pixels
   */
  public double worldToScreenY(double wy, double viewportHeight, double originY) {
    return worldToScreenY(wy, originY);
  }

  /**
   * Convert a screen-space X coordinate back to world-space.
   *
   * @param sx screen X in pixels
   * @return world X
   */
  public double screenToWorldX(double sx) {
    return sx / zoom + x;
  }

  /**
   * Convert a screen-space X coordinate back to world-space using an explicit origin.
   */
  public double screenToWorldX(double sx, double originX) {
    return screenToWorldX(sx - originX);
  }

  /**
   * Convert a screen-space X coordinate back to world-space.
   *
   * @param sx            screen X in pixels
   * @param viewportWidth viewport width in pixels (ignored; kept for compatibility)
   * @param originX       screen X of the camera origin
   * @return world X
   */
  public double screenToWorldX(double sx, double viewportWidth, double originX) {
    return screenToWorldX(sx, originX);
  }

  /**
   * Convert a screen-space Y coordinate back to world-space.
   *
   * @param sy screen Y in pixels
   * @return world Y
   */
  public double screenToWorldY(double sy) {
    return sy / zoom + y;
  }

  /**
   * Convert a screen-space Y coordinate back to world-space using an explicit origin.
   */
  public double screenToWorldY(double sy, double originY) {
    return screenToWorldY(sy - originY);
  }

  /**
   * Convert a screen-space Y coordinate back to world-space.
   *
   * @param sy             screen Y in pixels
   * @param viewportHeight viewport height in pixels (ignored; kept for compatibility)
   * @param originY        screen Y of the camera origin
   * @return world Y
   */
  public double screenToWorldY(double sy, double viewportHeight, double originY) {
    return screenToWorldY(sy, originY);
  }

  /**
   * Copy the currently visible world-space view rectangle into {@code out}.
   */
  public Rect viewRect(Rect out) {
    return viewRect(viewportWidth, viewportHeight, out);
  }

  /**
   * Copy the visible world-space view rectangle for an explicit logical viewport size.
   */
  public Rect viewRect(double viewportWidth, double viewportHeight, Rect out) {
    Rect result = out == null ? new Rect() : out;
    result.x = x;
    result.y = y;
    result.w = viewWidth(viewportWidth);
    result.h = viewHeight(viewportHeight);
    return result;
  }

  private static double sanitizeFinite(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }
}
