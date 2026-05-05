package com.jvn.core.assets;

/**
 * High-level façade for asset discovery and loading, delegating to a
 * pluggable {@link AssetManager} backend.
 *
 * <p>{@code AssetCatalog} adds convenience methods for listing assets by
 * type ({@link #listImages()}, {@link #listAudio()}, etc.) and provides a
 * static default manager that can be swapped at startup via
 * {@link #setDefaultManager(AssetManager)}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AssetCatalog catalog = new AssetCatalog();        // uses default classpath manager
 * boolean hasHero = catalog.exists(AssetType.IMAGE, "characters/hero.png");
 * InputStream in   = catalog.open(AssetType.AUDIO, "bgm/title.ogg");
 * }</pre>
 *
 * @see AssetManager
 * @see ClasspathAssetManager
 * @see FilesystemAssetManager
 */
public class AssetCatalog {

  /** The underlying asset manager this catalogue delegates to. */
  private final AssetManager manager;

  /** Global default manager used by the no-arg constructor. */
  private static AssetManager defaultManager = new ClasspathAssetManager();

  /** Construct a catalogue backed by the global default manager. */
  public AssetCatalog() {
    this(defaultManager);
  }

  /**
   * Construct a catalogue backed by a specific manager.
   *
   * @param manager the asset manager to delegate to
   */
  public AssetCatalog(AssetManager manager) {
    this.manager = manager;
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Core operations (delegate to manager)
  // ──────────────────────────────────────────────────────────────────────────

  /** @see AssetManager#exists(AssetType, String) */
  public boolean exists(AssetType type, String name) {
    return manager.exists(type, name);
  }

  /** @see AssetManager#url(AssetType, String) */
  public java.net.URL url(AssetType type, String name) {
    return manager.url(type, name);
  }

  /** @see AssetManager#open(AssetType, String) */
  public java.io.InputStream open(AssetType type, String name) throws java.io.IOException {
    return manager.open(type, name);
  }

  /** @see AssetManager#list(String) */
  public java.util.List<String> list(String directory) {
    return manager.list(directory);
  }

  // ──────────────────────────────────────────────────────────────────────────
  //  Type-specific listing shortcuts
  // ──────────────────────────────────────────────────────────────────────────

  /** @return immediate children of the images directory */
  public java.util.List<String> listImages() { return list(AssetPaths.IMAGES); }

  /** @return immediate children of the audio directory */
  public java.util.List<String> listAudio() { return list(AssetPaths.AUDIO); }

  /** @return immediate children of the scripts directory */
  public java.util.List<String> listScripts() { return list(AssetPaths.SCRIPTS); }

  /** @return immediate children of the fonts directory */
  public java.util.List<String> listFonts() { return list(AssetPaths.FONTS); }

  /** @return immediate children of the UI directory */
  public java.util.List<String> listUI() { return list(AssetPaths.UI); }

  /** @return immediate children of the video directory */
  public java.util.List<String> listVideo() { return list(AssetPaths.VIDEO); }

  /** @return immediate children of the config directory */
  public java.util.List<String> listConfig() { return list(AssetPaths.CONFIG); }

  // ──────────────────────────────────────────────────────────────────────────
  //  Global default manager
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Replace the global default {@link AssetManager} used by the no-arg
   * constructor. Typically called once at application startup.
   *
   * @param m the new default manager; {@code null} is ignored
   */
  public static void setDefaultManager(AssetManager m) {
    if (m != null) defaultManager = m;
  }
}
