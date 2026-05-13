package com.jvn.core.config;

/**
 * Runtime configuration for the visual novel engine.
 *
 * <p>Covers rendering tuning, async behaviour, and accessibility settings.
 * Persisted via {@code VnSaveMigration}: any new field must be added with a
 * default so existing saves round-trip cleanly.
 */
public class VnConfig {

  // ── Image cache ────────────────────────────────────────────────────────────

  /** Maximum number of images held in each renderer's LRU cache. Default 256. */
  private int imageCacheMaxEntries = 256;

  // ── Async rendering ────────────────────────────────────────────────────────

  /** When true, JavaFX Image objects are decoded on a background thread. */
  private boolean asyncImageDecode = true;

  // ── Accessibility ──────────────────────────────────────────────────────────

  /** Whether text-to-speech self-voicing is enabled. */
  private boolean ttsEnabled = false;

  /**
   * UI font scale multiplier, clamped to [0.75, 2.0].
   * Applied to every font size in VnRenderer and MenuRenderer.
   */
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

  public boolean isTtsEnabled() { return ttsEnabled; }

  public void setTtsEnabled(boolean ttsEnabled) { this.ttsEnabled = ttsEnabled; }

  public double getUiFontScale() { return uiFontScale; }

  public void setUiFontScale(double uiFontScale) {
    this.uiFontScale = Math.max(0.75, Math.min(2.0, uiFontScale));
  }

  /** Returns a new {@code VnConfig} with all fields at their default values. */
  public static VnConfig defaults() {
    return new VnConfig();
  }
}
