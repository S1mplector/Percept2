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
  private int stageLightingRecomposites = 0;

  public void reset() {
    characterLayerDraws = 0;
    otherDraws = 0;
    stageLightingRecomposites = 0;
  }

  public void incrementCharacterLayer() {
    characterLayerDraws++;
  }

  public void incrementOther() {
    otherDraws++;
  }

  /** Count a stage-lighting cache miss — a full per-pixel relight of a character layer. */
  public void incrementStageLightingRecomposite() {
    stageLightingRecomposites++;
  }

  public int getCharacterLayerDraws() { return characterLayerDraws; }
  public int getOtherDraws() { return otherDraws; }
  public int getTotalDraws() { return characterLayerDraws + otherDraws; }
  public int getStageLightingRecomposites() { return stageLightingRecomposites; }
}
