package com.jvn.core.scene2d;

import java.util.HashMap;
import java.util.Map;

/**
 * An animated character entity backed by a {@link SpriteSheet} with named
 * animation clips and an optional dialogue-trigger ID.
 *
 * <p>{@code CharacterEntity2D} extends {@link Entity2D} with:</p>
 * <ul>
 *   <li><b>Named animations</b> — a map of clip names (e.g. "walk_down", "idle")
 *       to arrays of tile indices. The current clip loops automatically.</li>
 *   <li><b>Dialogue ID</b> — an optional identifier that links this character
 *       to a VN dialogue script or interaction trigger.</li>
 *   <li><b>Bottom-centre origin</b> — the pivot defaults to (0.5, 1.0) so
 *       the position represents the character's feet.</li>
 * </ul>
 *
 * <h2>Animation Spec Format</h2>
 * <p>The static helper {@link #parseAnimations(String)} accepts a compact
 * string like {@code "down:0-3,right:4-7,idle:8"} and returns a ready-to-use
 * animation map.</p>
 *
 * @see SpriteSheet
 * @see SpriteAnimation2D
 */
public class CharacterEntity2D extends Entity2D {

  /** The sprite sheet containing all animation frames. */
  private final SpriteSheet sheet;

  /** Display width in logical pixels. */
  private final double drawW;

  /** Display height in logical pixels. */
  private final double drawH;

  /** Named animation clips: name → array of tile indices in the sheet. */
  private final Map<String, int[]> animations = new HashMap<>();

  /** Name of the currently playing animation clip (key into {@link #animations}). */
  private String currentAnim;

  /** Current frame index within the active clip's frame array. */
  private int frameIndex;

  /** Duration of each animation frame in milliseconds. */
  private double frameDurationMs = 120.0;

  /** Accumulated time since the last frame advance (ms). */
  private double frameElapsedMs = 0.0;

  /** Movement speed in world units per second (reserved for gameplay use). */
  private double speed = 0.0;

  /** Optional dialogue/interaction trigger ID for VN or RPG scripts. */
  private String dialogueId;

  /**
   * Construct a character entity with the given sprite sheet and display size.
   * The origin defaults to bottom-centre (0.5, 1.0).
   *
   * @param sheet sprite sheet containing animation frames
   * @param drawW display width in logical pixels
   * @param drawH display height in logical pixels
   */
  public CharacterEntity2D(SpriteSheet sheet, double drawW, double drawH) {
    this.sheet = sheet;
    this.drawW = drawW;
    this.drawH = drawH;
    this.originX = 0.5;
    this.originY = 1.0;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Accessors
  // ──────────────────────────────────────────────────────────────────────────

  /** @return display width */
  public double getDrawWidth() { return drawW; }

  /** @return display height */
  public double getDrawHeight() { return drawH; }

  /** @param speed movement speed in world units per second */
  public void setSpeed(double speed) {
    this.speed = speed;
  }

  /** @return movement speed in world units per second */
  public double getSpeed() {
    return speed;
  }

  /** @param id dialogue/interaction trigger ID, or {@code null} */
  public void setDialogueId(String id) {
    this.dialogueId = id;
  }

  /** @return the dialogue trigger ID, or {@code null} */
  public String getDialogueId() {
    return dialogueId;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Animation management
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Replace the animation set. If no animation is currently selected,
   * the first clip in the map is activated automatically.
   *
   * @param anims map of clip name → frame index arrays; may be {@code null}
   */
  public void setAnimations(Map<String, int[]> anims) {
    animations.clear();
    if (anims != null) animations.putAll(anims);
    if (currentAnim == null && !animations.isEmpty()) {
      currentAnim = animations.keySet().iterator().next();
      frameIndex = 0;
      frameElapsedMs = 0.0;
    }
  }

  /**
   * Switch to a named animation clip. If the name is not found, the call
   * is silently ignored and the current animation continues.
   *
   * @param name the clip name (e.g. "walk_down")
   */
  public void setCurrentAnimation(String name) {
    if (name == null) return;
    int[] frames = animations.get(name);
    if (frames == null || frames.length == 0) return;
    currentAnim = name;
    frameIndex = 0;
    frameElapsedMs = 0.0;
  }

  /**
   * Set the per-frame duration for animation playback.
   *
   * @param ms milliseconds per frame; ≤ 0 freezes animation
   */
  public void setFrameDurationMs(double ms) {
    this.frameDurationMs = ms <= 0 ? 0 : ms;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Update & render
  // ──────────────────────────────────────────────────────────────────────────

  /** Advance the animation clock, cycling through frames of the current clip. */
  @Override
  public void update(long deltaMs) {
    super.update(deltaMs);
    if (frameDurationMs <= 0) return;
    int[] frames = currentFrames();
    if (frames == null || frames.length == 0) return;
    frameElapsedMs += deltaMs;
    while (frameElapsedMs >= frameDurationMs && frameDurationMs > 0) {
      frameElapsedMs -= frameDurationMs;
      frameIndex = (frameIndex + 1) % frames.length;
    }
  }

  /** Draw the current animation frame, offset by the bottom-centre origin. */
  @Override
  public void render(Blitter2D b) {
    int frame = currentFrame();
    if (frame < 0) return;
    b.push();
    double dx = -originX * drawW;
    double dy = -originY * drawH;
    sheet.drawTile(b, frame, dx, dy, drawW, drawH);
    b.pop();
  }

  /** @return the frame index array for the current animation, or {@code null} */
  private int[] currentFrames() {
    if (currentAnim == null) return null;
    return animations.get(currentAnim);
  }

  /** @return the tile index of the current frame, or -1 if none */
  private int currentFrame() {
    int[] frames = currentFrames();
    if (frames == null || frames.length == 0) return -1;
    int idx = frameIndex;
    if (idx < 0) idx = 0;
    if (idx >= frames.length) idx = frames.length - 1;
    return frames[idx];
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Static utilities
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Parse a compact animation spec string into a name → frame-index map.
   *
   * <p>Format: {@code "clipName:startIdx-endIdx, clipName2:singleIdx, ..."}
   * <br>Example: {@code "down:0-3,right:4-7,idle:8"}</p>
   *
   * @param spec comma-separated clip definitions; may be {@code null}
   * @return mutable map of clip name → frame index arrays (never {@code null})
   */
  public static Map<String, int[]> parseAnimations(String spec) {
    Map<String, int[]> out = new HashMap<>();
    if (spec == null) return out;
    String[] parts = spec.split(",");
    for (String p : parts) {
      String s = p.trim();
      if (s.isEmpty()) continue;
      int colon = s.indexOf(':');
      if (colon <= 0 || colon >= s.length() - 1) continue;
      String name = s.substring(0, colon).trim();
      String range = s.substring(colon + 1).trim();
      if (name.isEmpty() || range.isEmpty()) continue;
      int dash = range.indexOf('-');
      try {
        if (dash > 0) {
          int start = Integer.parseInt(range.substring(0, dash).trim());
          int end = Integer.parseInt(range.substring(dash + 1).trim());
          if (end < start) {
            int tmp = start; start = end; end = tmp;
          }
          int len = end - start + 1;
          int[] frames = new int[len];
          for (int i = 0; i < len; i++) frames[i] = start + i;
          out.put(name, frames);
        } else {
          int single = Integer.parseInt(range);
          out.put(name, new int[]{ single });
        }
      } catch (NumberFormatException ignored) {}
    }
    return out;
  }
}
