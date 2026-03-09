package com.jvn.core.scene2d;

/**
 * Base class for all 2D scene-graph entities (sprites, labels, panels, etc.).
 *
 * <p>{@code Entity2D} holds the core spatial properties shared by every visible
 * object in the engine's 2D world: position, rotation, scale, depth ordering,
 * parallax factors, and a transform origin. Subclasses add domain-specific
 * data (e.g. texture references, text content, particle parameters).</p>
 *
 * <h2>Transform Order</h2>
 * <p>When rendered, transforms are typically applied in this order:
 * <ol>
 *   <li>Translate to {@code (x, y)}</li>
 *   <li>Translate by {@code (-originX, -originY)} to move the pivot</li>
 *   <li>Rotate by {@link #rotationDeg} degrees</li>
 *   <li>Scale by {@code (scaleX, scaleY)}</li>
 * </ol>
 *
 * <h2>Parallax</h2>
 * <p>{@link #parallaxX} and {@link #parallaxY} multiply the camera offset
 * applied to this entity, enabling depth-layered scrolling effects:
 * 1.0 = normal scroll speed, 0.0 = fixed to screen (HUD), &gt;1.0 = foreground.</p>
 *
 * <h2>Depth Sorting</h2>
 * <p>{@link #z} determines draw order within a scene. Higher values are drawn
 * later (on top). The renderer sorts entities by {@code z} before drawing.</p>
 *
 * @see Sprite2D
 * @see Label2D
 * @see Scene2D
 * @see Blitter2D
 */
public class Entity2D {

  /** X position in world / logical coordinates. */
  protected double x;

  /** Y position in world / logical coordinates. */
  protected double y;

  /** Rotation in degrees (clockwise in screen-space). */
  protected double rotationDeg;

  /** Horizontal scale factor. 1.0 = natural size; negative values flip. */
  protected double scaleX = 1.0;

  /** Vertical scale factor. 1.0 = natural size; negative values flip. */
  protected double scaleY = 1.0;

  /** Depth value for draw-order sorting. Higher = drawn later (on top). */
  protected double z;

  /** Whether this entity should be drawn and receive updates. */
  protected boolean visible = true;

  /** Parallax multiplier for horizontal camera scrolling (1.0 = normal). */
  protected double parallaxX = 1.0;

  /** Parallax multiplier for vertical camera scrolling (1.0 = normal). */
  protected double parallaxY = 1.0;

  /** X component of the transform origin / pivot point (local space). */
  protected double originX = 0.0;

  /** Y component of the transform origin / pivot point (local space). */
  protected double originY = 0.0;

  // ──────────────────────────────────────────────────────────────────────────
  //  Getters
  // ──────────────────────────────────────────────────────────────────────────

  /** @return X position in world space */
  public double getX() { return x; }

  /** @return Y position in world space */
  public double getY() { return y; }

  /** @return rotation in degrees */
  public double getRotationDeg() { return rotationDeg; }

  /** @return horizontal scale factor */
  public double getScaleX() { return scaleX; }

  /** @return vertical scale factor */
  public double getScaleY() { return scaleY; }

  /** @return depth value for draw-order sorting */
  public double getZ() { return z; }

  /** @return {@code true} if this entity is visible and should be rendered */
  public boolean isVisible() { return visible; }

  /** @return horizontal parallax multiplier */
  public double getParallaxX() { return parallaxX; }

  /** @return vertical parallax multiplier */
  public double getParallaxY() { return parallaxY; }

  /** @return X component of the transform origin */
  public double getOriginX() { return originX; }

  /** @return Y component of the transform origin */
  public double getOriginY() { return originY; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Setters
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Set the entity's world position.
   *
   * @param x world X
   * @param y world Y
   */
  public void setPosition(double x, double y) { this.x = x; this.y = y; }

  /** @param deg rotation in degrees (clockwise in screen-space) */
  public void setRotationDeg(double deg) { this.rotationDeg = deg; }

  /**
   * Set non-uniform scale factors.
   *
   * @param sx horizontal scale (1.0 = natural, negative = flip)
   * @param sy vertical scale
   */
  public void setScale(double sx, double sy) { this.scaleX = sx; this.scaleY = sy; }

  /** @param z depth value for draw-order sorting */
  public void setZ(double z) { this.z = z; }

  /** @param visible whether this entity should be rendered */
  public void setVisible(boolean visible) { this.visible = visible; }

  /**
   * Set parallax scrolling multipliers.
   *
   * @param px horizontal multiplier (0 = HUD-fixed, 1 = normal, &gt;1 = foreground)
   * @param py vertical multiplier
   */
  public void setParallax(double px, double py) { this.parallaxX = px; this.parallaxY = py; }

  /**
   * Set the transform origin (pivot point) in local space.
   * Rotation and scale are applied around this point.
   *
   * @param ox origin X
   * @param oy origin Y
   */
  public void setOrigin(double ox, double oy) { this.originX = ox; this.originY = oy; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Lifecycle
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Called once per frame to update this entity's state (animation, AI, etc.).
   * Override in subclasses; the default implementation is a no-op.
   *
   * @param deltaMs elapsed frame time in milliseconds
   */
  public void update(long deltaMs) {}

  /**
   * Draw this entity using the provided {@link Blitter2D} abstraction.
   * Override in subclasses; the default implementation draws nothing.
   *
   * @param b the 2D rendering context
   */
  public void render(Blitter2D b) {}
}
