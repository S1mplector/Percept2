package com.jvn.core.config;

/**
 * Runtime configuration for the visual novel engine.
 *
 * <p>Covers process-level rendering and asynchronous behavior. Player
 * preferences, including accessibility settings, belong to
 * {@code com.jvn.core.vn.VnSettings}.
 */
public class VnConfig {

  // ── Image cache ────────────────────────────────────────────────────────────

  /** Maximum number of images held in each renderer's LRU cache. Default 256. */
  private int imageCacheMaxEntries = 256;

  // ── Async rendering ────────────────────────────────────────────────────────

  /** When true, JavaFX Image objects are decoded on a background thread. */
  private boolean asyncImageDecode = true;

  // ── Accessibility ──────────────────────────────────────────────────────────

  /** @deprecated Use {@code VnSettings.isTextToSpeechEnabled()}. */
  @Deprecated(forRemoval = true)
  private boolean ttsEnabled = false;

  /**
   * @deprecated Use {@code VnSettings.getUiFontScale()}. This compatibility
   * field is not consumed by the runtime renderer.
   */
  @Deprecated(forRemoval = true)
  private double uiFontScale = 1.0;

  // ── Accessors ──────────────────────────────────────────────────────────────

  public int getImageCacheMaxEntries() { return imageCacheMaxEntries; }

  public void setImageCacheMaxEntries(int imageCacheMaxEntries) {
    this.imageCacheMaxEntries = Math.max(1, imageCacheMaxEntries);
  }

  public boolean isAsyncImageDecode() { return asyncImageDecode; }

  public void setAsyncImageDecode(boolean asyncImageDecode) {
    this.asyncImageDecode = asyncImageDecode;
  }

  /** @deprecated Use the active {@code VnSettings} instance. */
  @Deprecated(forRemoval = true)
  public boolean isTtsEnabled() { return ttsEnabled; }

  /** @deprecated Use the active {@code VnSettings} instance. */
  @Deprecated(forRemoval = true)
  public void setTtsEnabled(boolean ttsEnabled) { this.ttsEnabled = ttsEnabled; }

  /** @deprecated Use the active {@code VnSettings} instance. */
  @Deprecated(forRemoval = true)
  public double getUiFontScale() { return uiFontScale; }

  /** @deprecated Use the active {@code VnSettings} instance. */
  @Deprecated(forRemoval = true)
  public void setUiFontScale(double uiFontScale) {
    this.uiFontScale = Math.max(0.75, Math.min(2.0, uiFontScale));
  }

  /** Returns a new {@code VnConfig} with all fields at their default values. */
  public static VnConfig defaults() {
    return new VnConfig();
  }
}
