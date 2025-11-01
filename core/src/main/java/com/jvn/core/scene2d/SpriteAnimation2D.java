package com.jvn.core.scene2d;

public class SpriteAnimation2D extends Entity2D {
  private final SpriteSheet sheet;
  private final int startIndex;
  private final int frameCount;
  private final long frameDurationMs;
  private boolean loop = true;
  private boolean playing = true;
  private long elapsedMs = 0;
  private int currentFrame = 0;
  private double width;
  private double height;
  private double alpha = 1.0;

  public SpriteAnimation2D(SpriteSheet sheet, int startIndex, int frameCount, long frameDurationMs, double drawWidth, double drawHeight) {
    this.sheet = sheet;
    this.startIndex = Math.max(0, startIndex);
    this.frameCount = Math.max(1, frameCount);
    this.frameDurationMs = Math.max(1, frameDurationMs);
    this.width = drawWidth;
    this.height = drawHeight;
  }

  public void setLoop(boolean loop) { this.loop = loop; }
  public void setPlaying(boolean playing) { this.playing = playing; }
  public void setAlpha(double a) { this.alpha = a; }

  @Override
  public void update(long deltaMs) {
    if (!playing) return;
    elapsedMs += deltaMs;
    while (elapsedMs >= frameDurationMs) {
      elapsedMs -= frameDurationMs;
      currentFrame++;
      if (currentFrame >= frameCount) {
        if (loop) currentFrame = 0; else { currentFrame = frameCount - 1; playing = false; }
      }
    }
  }

  @Override
  public void render(Blitter2D b) {
    b.push();
    if (alpha != 1.0) b.setGlobalAlpha(alpha);
    sheet.drawTile(b, startIndex + currentFrame, 0, 0, width, height);
    b.pop();
  }
}
