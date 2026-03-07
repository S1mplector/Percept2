package com.jvn.audiofx;

/**
 * Beez-facing ambience provider using the shared FX ambience DSP core.
 * This effectively folds Loom-style ambience generation into the Beez backend layer.
 */
public final class BeezAmbienceSynth extends FxAmbienceSynth {
  public BeezAmbienceSynth() {
    super("beez", "audiofx-beez-ambience");
  }
}

