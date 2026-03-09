package com.jvn.core.graphics;

/**
 * 2D camera with smooth follow, zoom, and optional world-space bounds clamping.
 *
 * <p>The camera maintains a <b>current position</b> ({@link #getX()}, {@link #getY()})
 * and a <b>target position</b> ({@link #getTargetX()}, {@link #getTargetY()}).
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
 * When enabled, the camera position is clamped after every update and teleport,
 * preventing the view from scrolling past the edge of the game world.</p>
 *
 * <h2>Coordinate Transforms</h2>
 * <p>{@link #worldToScreenX}/{@link #worldToScreenY} and
 * {@link #screenToWorldX}/{@link #screenToWorldY} convert between world-space
 * coordinates and pixel-space screen coordinates, accounting for the camera's
 * current position and zoom level.</p>
 *
 * @see ViewportScaler2D
 */
public class Camera2D {

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

  // ──────────────────────────────────────────────────────────────────────────
  //  Position & zoom accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return current camera X in world space */
  public double getX() { return x; }

  /** @return current camera Y in world space */
  public double getY() { return y; }

  /** @return current zoom factor (always > 0) */
  public double getZoom() { return zoom; }

  /**
   * Teleport the camera (and target) to an exact position immediately.
   * The position is clamped to bounds if enabled.
   *
   * @param x world X
   * @param y world Y
   */
  public void setPosition(double x, double y) { this.x = x; this.y = y; this.targetX = x; this.targetY = y; clampToBounds(); }

  /**
   * Set the zoom factor. Values ≤ 0 are clamped to a tiny positive epsilon
   * to prevent division-by-zero in coordinate transforms.
   *
   * @param z desired zoom factor
   */
  public void setZoom(double z) { this.zoom = z <= 0 ? 0.0001 : z; }

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
  public void setTarget(double x, double y) { this.targetX = x; this.targetY = y; }

  /** @return the target X the camera is moving toward */
  public double getTargetX() { return targetX; }

  /** @return the target Y the camera is moving toward */
  public double getTargetY() { return targetY; }

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
    if (x < boundLeft) x = boundLeft;
    if (y < boundTop) y = boundTop;
    if (x > boundRight) x = boundRight;
    if (y > boundBottom) y = boundBottom;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Coordinate transforms
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Convert a world-space X coordinate to screen-space pixels.
   *
   * @param wx            world X coordinate
   * @param viewportWidth viewport width in pixels (reserved for future use)
   * @param originX       screen X of the camera origin (typically viewport centre)
   * @return screen X in pixels
   */
  public double worldToScreenX(double wx, double viewportWidth, double originX) {
    return (wx - x) * zoom + originX;
  }

  /**
   * Convert a world-space Y coordinate to screen-space pixels.
   *
   * @param wy             world Y coordinate
   * @param viewportHeight viewport height in pixels (reserved for future use)
   * @param originY        screen Y of the camera origin (typically viewport centre)
   * @return screen Y in pixels
   */
  public double worldToScreenY(double wy, double viewportHeight, double originY) {
    return (wy - y) * zoom + originY;
  }

  /**
   * Convert a screen-space X coordinate back to world-space.
   *
   * @param sx            screen X in pixels
   * @param viewportWidth viewport width in pixels (reserved for future use)
   * @param originX       screen X of the camera origin
   * @return world X
   */
  public double screenToWorldX(double sx, double viewportWidth, double originX) {
    return (sx - originX) / zoom + x;
  }

  /**
   * Convert a screen-space Y coordinate back to world-space.
   *
   * @param sy             screen Y in pixels
   * @param viewportHeight viewport height in pixels (reserved for future use)
   * @param originY        screen Y of the camera origin
   * @return world Y
   */
  public double screenToWorldY(double sy, double viewportHeight, double originY) {
    return (sy - originY) / zoom + y;
  }
}
