package com.jvn.core.scene2d;
import java.util.HashMap;
import java.util.Map;

/**
 * A frame-based sprite animation entity backed by a {@link SpriteSheet}.
 *
 * <p>{@code SpriteAnimation2D} plays a contiguous range of frames from a sprite
 * sheet at a fixed frame duration, supporting loop, one-shot, and
 * ping-pong (forward-then-backward) playback modes.</p>
 *
 * <h2>Frame Events</h2>
 * <p>Callbacks can be registered for specific frames via {@link #onFrame(int, Runnable)}.
 * These fire exactly once each time the animation reaches that frame — useful for
 * syncing sound effects, particle bursts, or gameplay triggers to animation timing.</p>
 *
 * <h2>Playback Modes</h2>
 * <ul>
 *   <li><b>Loop</b> (default) — wraps from last frame back to first frame.</li>
 *   <li><b>One-shot</b> ({@code setLoop(false)}) — stops on the last frame and sets
 *       {@code playing = false}.</li>
 *   <li><b>Ping-pong</b> ({@code setPingPong(true)}) — reverses direction at the
 *       ends of the frame range, creating a back-and-forth oscillation.</li>
 * </ul>
 *
 * @see SpriteSheet
 * @see Sprite2D
 */
public class SpriteAnimation2D extends Entity2D {

  /** The sprite sheet that provides the animation frames. */
  private final SpriteSheet sheet;

  /** Index of the first frame in the sheet for this animation. */
  private final int startIndex;

  /** Total number of frames in this animation clip. */
  private final int frameCount;

  /** Duration of each frame in milliseconds. */
  private final long frameDurationMs;

  /** Whether the animation loops when it reaches the last frame. */
  private boolean loop = true;

  /** Whether the animation is currently advancing. */
  private boolean playing = true;

  /** Accumulated time since the last frame advance (ms). */
  private long elapsedMs = 0;

  /** Current frame index (0-based, relative to this animation clip). */
  private int currentFrame = 0;

  /** Whether to use ping-pong (oscillating) playback. */
  private boolean pingPong = false;

  /** Direction of frame advancement: +1 = forward, -1 = backward (ping-pong). */
  private int direction = 1;

  /** Per-frame event callbacks, keyed by local frame index. */
  private final Map<Integer, Runnable> frameEvents = new HashMap<>();

  /** Display width in logical pixels. */
  private double width;

  /** Display height in logical pixels. */
  private double height;

  /** Opacity multiplier [0.0, 1.0]. */
  private double alpha = 1.0;

  /**
   * Construct a sprite animation from a contiguous range of sheet frames.
   *
   * @param sheet           the sprite sheet containing the frames
   * @param startIndex      index of the first frame (clamped to ≥ 0)
   * @param frameCount      number of frames in the clip (clamped to ≥ 1)
   * @param frameDurationMs duration of each frame in ms (clamped to ≥ 1)
   * @param drawWidth       display width in logical pixels
   * @param drawHeight      display height in logical pixels
   */
  public SpriteAnimation2D(SpriteSheet sheet, int startIndex, int frameCount, long frameDurationMs, double drawWidth, double drawHeight) {
    this.sheet = sheet;
    this.startIndex = Math.max(0, startIndex);
    this.frameCount = Math.max(1, frameCount);
    this.frameDurationMs = Math.max(1, frameDurationMs);
    this.width = drawWidth;
    this.height = drawHeight;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Playback controls
  // ──────────────────────────────────────────────────────────────────────────

  /** @param loop {@code true} to loop, {@code false} for one-shot */
  public void setLoop(boolean loop) { this.loop = loop; }

  /** @param playing {@code true} to play, {@code false} to pause */
  public void setPlaying(boolean playing) { this.playing = playing; }

  /** @param a opacity [0.0, 1.0] */
  public void setAlpha(double a) { this.alpha = a; }

  /** @return display width in logical pixels */
  public double getWidth() { return width; }

  /** @return display height in logical pixels */
  public double getHeight() { return height; }

  /** @param pingPong {@code true} to oscillate between first and last frame */
  public void setPingPong(boolean pingPong) { this.pingPong = pingPong; }

  /**
   * Register a callback to fire when the animation reaches a specific frame.
   *
   * @param localFrameIndex 0-based frame index within this animation clip
   * @param cb              the callback to invoke; must not be {@code null}
   */
  public void onFrame(int localFrameIndex, Runnable cb) { if (localFrameIndex >= 0 && localFrameIndex < frameCount && cb != null) frameEvents.put(localFrameIndex, cb); }

  /** Remove all registered frame-event callbacks. */
  public void clearFrameEvents() { frameEvents.clear(); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Update & render
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Advance the animation clock and step through frames as needed.
   * Frame events are fired synchronously when their frame is reached.
   */
  @Override
  public void update(long deltaMs) {
    if (!playing) return;
    elapsedMs += deltaMs;
    while (elapsedMs >= frameDurationMs) {
      elapsedMs -= frameDurationMs;
      advanceFrame();
    }
  }

  /** Draw the current animation frame using the blitter. */
  @Override
  public void render(Blitter2D b) {
    b.push();
    if (alpha != 1.0) b.setGlobalAlpha(alpha);
    sheet.drawTile(b, startIndex + currentFrame, 0, 0, width, height);
    b.pop();
  }

  /**
   * Step to the next frame, handling loop / one-shot / ping-pong logic.
   * Fires registered frame-event callbacks when a frame is reached.
   */
  private void advanceFrame() {
    if (!pingPong) {
      currentFrame++;
      if (currentFrame >= frameCount) {
        if (loop) currentFrame = 0; else { currentFrame = frameCount - 1; playing = false; }
      }
    } else {
      currentFrame += direction;
      if (currentFrame >= frameCount) {
        currentFrame = frameCount - 2;
        direction = -1;
        if (currentFrame < 0) currentFrame = 0;
        if (!loop && currentFrame == 0) playing = false;
      } else if (currentFrame < 0) {
        currentFrame = 1;
        direction = 1;
        if (!loop && currentFrame == frameCount - 1) playing = false;
      }
    }
    Runnable cb = frameEvents.get(currentFrame);
    if (cb != null) cb.run();
  }
}
