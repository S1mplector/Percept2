package com.jvn.audiofx;

/**
 * Backward-compatible Loom alias for ambience synth provider selection.
 * Internally uses the shared FX ambience synthesizer implementation.
 */
public final class LoomAmbienceSynth extends FxAmbienceSynth {
  public LoomAmbienceSynth() {
    super("loom", "audiofx-loom-ambience");
  }
}

