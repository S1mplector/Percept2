package com.jvn.core.scene2d;

import java.util.HashMap;
import java.util.Map;

public class CharacterEntity2D extends Entity2D {
  private final SpriteSheet sheet;
  private final double drawW;
  private final double drawH;
  private double originX = 0.5;
  private double originY = 1.0;

  private final Map<String, int[]> animations = new HashMap<>();
  private String currentAnim;
  private int frameIndex;
  private double frameDurationMs = 120.0;
  private double frameElapsedMs = 0.0;

  private double speed = 0.0; // world units per second (not yet used)

  private String dialogueId;

  public CharacterEntity2D(SpriteSheet sheet, double drawW, double drawH) {
    this.sheet = sheet;
    this.drawW = drawW;
    this.drawH = drawH;
  }

  public double getDrawWidth() { return drawW; }
  public double getDrawHeight() { return drawH; }
  public double getOriginX() { return originX; }
  public double getOriginY() { return originY; }

  public void setOrigin(double ox, double oy) {
    this.originX = ox;
    this.originY = oy;
  }

  public void setSpeed(double speed) {
    this.speed = speed;
  }

  public double getSpeed() {
    return speed;
  }

  public void setDialogueId(String id) {
    this.dialogueId = id;
  }

  public String getDialogueId() {
    return dialogueId;
  }

  public void setAnimations(Map<String, int[]> anims) {
    animations.clear();
    if (anims != null) animations.putAll(anims);
    if (currentAnim == null && !animations.isEmpty()) {
      currentAnim = animations.keySet().iterator().next();
      frameIndex = 0;
      frameElapsedMs = 0.0;
    }
  }

  public void setCurrentAnimation(String name) {
    if (name == null) return;
    int[] frames = animations.get(name);
    if (frames == null || frames.length == 0) return;
    currentAnim = name;
    frameIndex = 0;
    frameElapsedMs = 0.0;
  }

  public void setFrameDurationMs(double ms) {
    this.frameDurationMs = ms <= 0 ? 0 : ms;
  }

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

  private int[] currentFrames() {
    if (currentAnim == null) return null;
    return animations.get(currentAnim);
  }

  private int currentFrame() {
    int[] frames = currentFrames();
    if (frames == null || frames.length == 0) return -1;
    int idx = frameIndex;
    if (idx < 0) idx = 0;
    if (idx >= frames.length) idx = frames.length - 1;
    return frames[idx];
  }

  /** Utility to parse a simple animation spec like "down:0-3,right:4-7" into a map. */
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
