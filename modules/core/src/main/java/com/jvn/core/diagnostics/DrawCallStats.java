package com.jvn.core.diagnostics;

/**
 * Per-frame draw call counter for the F3 performance HUD.
 *
 * <p>Distinguishes character-layer draws (sprite crossfades, per-layer expression
 * composites) from other scene draws (background, particles, audio visualizer),
 * so a heavy layered-character scene shows up distinctly from a heavy background/effects
 * scene. Call {@link #reset()} once at the start of each frame's render pass.</p>
 */
public final class DrawCallStats {

  private int characterLayerDraws = 0;
  private int otherDraws = 0;

  public void reset() {
    characterLayerDraws = 0;
    otherDraws = 0;
  }

  public void incrementCharacterLayer() {
    characterLayerDraws++;
  }

  public void incrementOther() {
    otherDraws++;
  }

  public int getCharacterLayerDraws() { return characterLayerDraws; }
  public int getOtherDraws() { return otherDraws; }
  public int getTotalDraws() { return characterLayerDraws + otherDraws; }
}
