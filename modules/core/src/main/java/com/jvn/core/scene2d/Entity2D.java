package com.jvn.core.scene2d;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
  private static final double[] IDENTITY_COLOR_MATRIX = new double[] {
      1.0, 0.0, 0.0, 0.0, 0.0,
      0.0, 1.0, 0.0, 0.0, 0.0,
      0.0, 0.0, 1.0, 0.0, 0.0,
      0.0, 0.0, 0.0, 1.0, 0.0
  };
  private static final AnimatablePropertyRegistry<Entity2D> PROPERTY_REGISTRY =
      new AnimatablePropertyRegistry<>();

  static {
    registerAnimatableProperty("matrix.mxx", 1.0, Entity2D::getMatrixMxx, Entity2D::setMatrixMxx);
    registerAnimatableProperty("matrix.mxy", 0.0, Entity2D::getMatrixMxy, Entity2D::setMatrixMxy);
    registerAnimatableProperty("matrix.myx", 0.0, Entity2D::getMatrixMyx, Entity2D::setMatrixMyx);
    registerAnimatableProperty("matrix.myy", 1.0, Entity2D::getMatrixMyy, Entity2D::setMatrixMyy);
    registerAnimatableProperty("matrix.tx", 0.0, Entity2D::getMatrixTx, Entity2D::setMatrixTx);
    registerAnimatableProperty("matrix.ty", 0.0, Entity2D::getMatrixTy, Entity2D::setMatrixTy);
    registerAnimatableProperty("effect.blur", 0.0, Entity2D::getBlurRadius, Entity2D::setBlurRadius);
    for (int row = 0; row < 4; row++) {
      for (int col = 0; col < 5; col++) {
        final int index = row * 5 + col;
        final double defaultValue = IDENTITY_COLOR_MATRIX[index];
        registerAnimatableProperty(
            "color.m" + row + col,
            defaultValue,
            e -> e.getColorMatrixValue(index),
            (e, value) -> e.setColorMatrixValue(index, value));
      }
    }
  }

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

  /** Supplemental affine matrix applied after the standard TRS transform. */
  protected double matrixMxx = 1.0;
  protected double matrixMxy = 0.0;
  protected double matrixMyx = 0.0;
  protected double matrixMyy = 1.0;
  protected double matrixTx = 0.0;
  protected double matrixTy = 0.0;

  /** Full 4x5 colour matrix applied to image-backed entities. */
  protected final double[] colorMatrix = IDENTITY_COLOR_MATRIX.clone();

  /** Base blur radius in logical pixels. */
  protected double blurRadius = 0.0;

  /** Arbitrary numeric custom properties that do not have registered handlers. */
  protected final Map<String, Double> customProperties = new LinkedHashMap<>();

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

  public double getMatrixMxx() { return matrixMxx; }
  public double getMatrixMxy() { return matrixMxy; }
  public double getMatrixMyx() { return matrixMyx; }
  public double getMatrixMyy() { return matrixMyy; }
  public double getMatrixTx() { return matrixTx; }
  public double getMatrixTy() { return matrixTy; }
  public double getBlurRadius() { return blurRadius; }

  public double[] getColorMatrix() {
    return colorMatrix.clone();
  }

  public double getColorMatrixValue(int index) {
    if (index < 0 || index >= colorMatrix.length) return 0.0;
    return colorMatrix[index];
  }

  public boolean hasSupplementalTransform() {
    return Math.abs(matrixMxx - 1.0) > 1e-9
        || Math.abs(matrixMxy) > 1e-9
        || Math.abs(matrixMyx) > 1e-9
        || Math.abs(matrixMyy - 1.0) > 1e-9
        || Math.abs(matrixTx) > 1e-9
        || Math.abs(matrixTy) > 1e-9;
  }

  public boolean hasNonIdentityColorMatrix() {
    for (int i = 0; i < colorMatrix.length; i++) {
      if (Math.abs(colorMatrix[i] - IDENTITY_COLOR_MATRIX[i]) > 1e-9) return true;
    }
    return false;
  }

  public Map<String, Double> getCustomPropertiesView() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(customProperties));
  }

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

  public void setSupplementalTransform(double mxx, double mxy, double myx, double myy, double tx, double ty) {
    this.matrixMxx = sanitizeFinite(mxx, 1.0);
    this.matrixMxy = sanitizeFinite(mxy, 0.0);
    this.matrixMyx = sanitizeFinite(myx, 0.0);
    this.matrixMyy = sanitizeFinite(myy, 1.0);
    this.matrixTx = sanitizeFinite(tx, 0.0);
    this.matrixTy = sanitizeFinite(ty, 0.0);
  }

  public void resetSupplementalTransform() {
    setSupplementalTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0);
  }

  public void setMatrixMxx(double value) { this.matrixMxx = sanitizeFinite(value, 1.0); }
  public void setMatrixMxy(double value) { this.matrixMxy = sanitizeFinite(value, 0.0); }
  public void setMatrixMyx(double value) { this.matrixMyx = sanitizeFinite(value, 0.0); }
  public void setMatrixMyy(double value) { this.matrixMyy = sanitizeFinite(value, 1.0); }
  public void setMatrixTx(double value) { this.matrixTx = sanitizeFinite(value, 0.0); }
  public void setMatrixTy(double value) { this.matrixTy = sanitizeFinite(value, 0.0); }

  public void setColorMatrix(double[] matrix) {
    if (matrix == null || matrix.length < colorMatrix.length) {
      System.arraycopy(IDENTITY_COLOR_MATRIX, 0, colorMatrix, 0, colorMatrix.length);
      return;
    }
    for (int i = 0; i < colorMatrix.length; i++) {
      colorMatrix[i] = sanitizeFinite(matrix[i], IDENTITY_COLOR_MATRIX[i]);
    }
  }

  public void resetColorMatrix() {
    setColorMatrix(IDENTITY_COLOR_MATRIX);
  }

  public void setColorMatrixValue(int index, double value) {
    if (index < 0 || index >= colorMatrix.length) return;
    colorMatrix[index] = sanitizeFinite(value, IDENTITY_COLOR_MATRIX[index]);
  }

  public void setBlurRadius(double blurRadius) {
    this.blurRadius = Math.max(0.0, sanitizeFinite(blurRadius, 0.0));
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
    AnimatablePropertyRegistry.Definition<Entity2D> definition = PROPERTY_REGISTRY.get(key);
    if (definition != null) {
      definition.setValue(this, value);
    } else {
      setCustomProperty(key, value);
    }
  }

  public double readCustomProperty(String key) {
    if (key == null || key.isBlank()) return 0.0;
    AnimatablePropertyRegistry.Definition<Entity2D> definition = PROPERTY_REGISTRY.get(key);
    if (definition != null) return definition.getValue(this);
    return getCustomProperty(key);
  }

  public static AnimatablePropertyRegistry.Definition<Entity2D> registerAnimatableProperty(
      String key,
      double defaultValue,
      java.util.function.ToDoubleFunction<Entity2D> getter,
      java.util.function.ObjDoubleConsumer<Entity2D> setter
  ) {
    return PROPERTY_REGISTRY.register(key, defaultValue, getter, setter);
  }

  public static AnimatablePropertyRegistry.Definition<Entity2D> getAnimatableProperty(String key) {
    return PROPERTY_REGISTRY.get(key);
  }

  public static Collection<AnimatablePropertyRegistry.Definition<Entity2D>> animatableProperties() {
    return PROPERTY_REGISTRY.definitions();
  }

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

  private static double sanitizeFinite(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }
}
