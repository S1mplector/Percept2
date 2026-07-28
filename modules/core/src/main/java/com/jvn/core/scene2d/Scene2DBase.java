package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.input.Input;

/**
 * Ready-to-use base implementation of {@link Scene2D} with a flat entity list,
 * optional camera, and automatic depth-sorted rendering.
 *
 * <p>{@code Scene2DBase} provides the standard "add entities → update → render"
 * workflow out of the box. Subclasses typically override {@link #onEnter()} to
 * set up entities and {@link #update(long)} (calling {@code super}) to add
 * game-specific logic.</p>
 *
 * <h2>Render Pipeline</h2>
 * <ol>
 *   <li>Sort children by {@link Entity2D#getZ()} (ascending = back to front).</li>
 *   <li>Apply camera translation and zoom.</li>
 *   <li>For each visible child:
 *     <ul>
 *       <li>Apply parallax offset (if camera is set).</li>
 *       <li>Apply entity position, rotation, and scale.</li>
 *       <li>Call {@link Entity2D#render(Blitter2D)}.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @see Scene2D
 * @see Entity2D
 * @see Camera2D
 */
public class Scene2DBase implements Scene2D {

  /** Flat list of all entities in this scene (unordered; sorted by Z at render time). */
  protected final List<Entity2D> children = new ArrayList<>();

  /** Optional 2D camera for scrolling and zoom; may be {@code null}. */
  protected Camera2D camera;

  /** Optional shared input reference; may be {@code null}. */
  protected Input input;
  
  /**
   * New variables from the new method defined in Entity2D.java
   * */
   
  private static final Comparator<Entity2D> Z_COMPARATOR = (a,b) ->
      Double.compare(a.getZ(), b.getZ());
  private final double[] scratchColorMatrix = new double[20];

  /** Entity mutations requested from update/render callbacks, applied after traversal. */
  private final List<Runnable> pendingChildMutations = new ArrayList<>();
  private int traversalDepth;

  // ──────────────────────────────────────────────────────────────────────────
  //  Camera & input wiring
  // ──────────────────────────────────────────────────────────────────────────

  /** @param camera the camera to use for scrolling/zoom, or {@code null} */
  public void setCamera(Camera2D camera) { this.camera = camera; }

  /** @return the current camera, or {@code null} */
  public Camera2D getCamera() { return camera; }

  /** @param input shared input state for this scene */
  public void setInput(Input input) { this.input = input; }

  /** @return the shared input state, or {@code null} */
  public Input getInput() { return input; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Entity management
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Add an entity to this scene. It will be updated and rendered each frame.
   *
   * @param e the entity to add; {@code null} is silently ignored
   */
  public void add(Entity2D e) {
    if (e == null) return;
    mutateChildren(() -> children.add(e));
  }

  /**
   * Remove an entity from this scene. No-op if not present.
   *
   * @param e the entity to remove
   */
  public void remove(Entity2D e) {
    if (e == null) return;
    mutateChildren(() -> children.remove(e));
  }

  /** Remove all entities from this scene. */
  public void clear() { mutateChildren(children::clear); }

  /** @return the live (mutable) list of child entities */
  public java.util.List<Entity2D> getChildren() { return children; }

  // ──────────────────────────────────────────────────────────────────────────
  //  Scene lifecycle
  // ──────────────────────────────────────────────────────────────────────────

  @Override public void onEnter() {}
  @Override public void onExit() {}

  /**
   * Update the camera (if set) and all child entities.
   *
   * <p>Subclasses should call {@code super.update(deltaMs)} to preserve
   * automatic camera and entity updates.</p>
   */
  @Override
  public void update(long deltaMs) {
    beginTraversal();
    try {
      if (camera != null) camera.update(Math.max(0L, deltaMs));
      int childCount = children.size();
      for (int i = 0; i < childCount; i++) {
        children.get(i).update(Math.max(0L, deltaMs));
      }
    } finally {
      endTraversal();
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Rendering
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Render all visible children sorted by Z depth, applying camera transforms
   * and per-entity parallax offsets.
   *
   * <p>The sort is performed every frame (O(n log n)) to ensure correct draw
   * order even when entities change Z dynamically. For each entity, the blitter
   * state is isolated with push/pop so transforms do not leak between siblings.</p>
   */
  @Override
  public void render(Blitter2D b, double width, double height) {
    if (b == null) return;
    beginTraversal();
    try {
      // Sort only when order actually changed; most frames remain allocation-free.
      boolean zDirty = false;
      for (int i = 1; i < children.size(); i++) {
        if (children.get(i - 1).getZ() > children.get(i).getZ()) {
          zDirty = true;
          break;
        }
      }
      if (zDirty) children.sort(Z_COMPARATOR);

      b.push();
      try {
        if (camera != null) {
          camera.setViewportSize(width, height);
          b.translate(-camera.getX(), -camera.getY());
          b.scale(camera.getZoom(), camera.getZoom());
        }
        int childCount = children.size();
        for (int i = 0; i < childCount; i++) {
          Entity2D e = children.get(i);
          if (!e.isVisible()) continue;
          b.push();
          try {
            renderEntity(b, e);
          } finally {
            // Never leak transforms, colour matrices, blur, or blend state when
            // an entity renderer fails.
            b.pop();
          }
        }
      } finally {
        b.pop();
      }
    } finally {
      endTraversal();
    }
  }

  private void renderEntity(Blitter2D b, Entity2D e) {
    // Apply parallax: offset the entity position relative to camera movement.
    // A parallaxX of 0.0 makes the entity fixed to the screen (HUD-like).
    if (camera != null) {
      double ox = camera.getX() * (1.0 - e.getParallaxX());
      double oy = camera.getY() * (1.0 - e.getParallaxY());
      if (ox != 0 || oy != 0) b.translate(ox, oy);
    }
    b.translate(e.getX(), e.getY());
    if (e.getRotationDeg() != 0) b.rotateDeg(e.getRotationDeg());
    if (e.getScaleX() != 1.0 || e.getScaleY() != 1.0) b.scale(e.getScaleX(), e.getScaleY());
    if (e.hasSupplementalTransform()) {
      b.transform(
          e.getMatrixMxx(),
          e.getMatrixMyx(),
          e.getMatrixMxy(),
          e.getMatrixMyy(),
          e.getMatrixTx(),
          e.getMatrixTy());
    }
    double brightness = e.getBrightness();
    if (e.hasNonIdentityColorMatrix() || Math.abs(brightness - 1.0) > 1e-9) {
      e.getColorMatrix(scratchColorMatrix);
      if (Math.abs(brightness - 1.0) > 1e-9) {
        applyBrightness(scratchColorMatrix, brightness);
      }
      b.setColorMatrix(scratchColorMatrix);
    } else {
      b.clearColorMatrix();
    }
    double blurRadius = e.getBlurRadius();
    if (camera != null && camera.hasDepthOfField()) {
      double depthDistance = Math.abs(e.getZ() - camera.getFocusDepth());
      double dofBlur = Math.min(
          camera.getDepthOfFieldMaxBlur(),
          depthDistance * camera.getDepthOfFieldStrength());
      blurRadius += Math.max(0.0, dofBlur);
    }
    b.setBlurRadius(blurRadius > 1e-9 ? blurRadius : 0.0);
    e.render(b);
  }

  private void beginTraversal() {
    traversalDepth++;
  }

  private void endTraversal() {
    traversalDepth--;
    if (traversalDepth != 0 || pendingChildMutations.isEmpty()) return;
    for (int i = 0; i < pendingChildMutations.size(); i++) {
      pendingChildMutations.get(i).run();
    }
    pendingChildMutations.clear();
  }

  private void mutateChildren(Runnable mutation) {
    if (traversalDepth > 0) {
      pendingChildMutations.add(mutation);
    } else {
      mutation.run();
    }
  }

  private static void applyBrightness(double[] colorMatrix, double brightness) {
    if (colorMatrix == null || colorMatrix.length < 20) return;
    double safeBrightness = Double.isFinite(brightness) ? Math.max(0.0, brightness) : 1.0;
    for (int row = 0; row < 3; row++) {
      int offset = row * 5;
      for (int col = 0; col < 5; col++) {
        colorMatrix[offset + col] *= safeBrightness;
      }
    }
  }
}
