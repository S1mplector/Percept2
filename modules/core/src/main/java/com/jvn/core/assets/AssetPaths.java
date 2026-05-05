package com.jvn.core.assets;

/**
 * Conventional directory constants and path-building utilities for the
 * engine's asset tree.
 *
 * <p>The standard layout is:</p>
 * <pre>
 * game/
 *   images/       ← backgrounds, sprites, UI textures
 *   audio/        ← music, SFX, voice
 *   scripts/      ← VNS / JES scripts
 *   fonts/        ← TTF / OTF typefaces
 *   ui/           ← nine-patch, icons, button skins
 *   video/        ← cutscenes
 *   config/       ← settings, profiles
 * </pre>
 *
 * @see AssetType
 * @see AssetManager
 */
public final class AssetPaths {

  /** Non-instantiable utility class. */
  private AssetPaths() {}

  /** Root of the asset tree inside the classpath / project. */
  public static final String BASE = "game/";

  /** Image assets directory. */
  public static final String IMAGES = BASE + "images/";

  /** Audio assets directory. */
  public static final String AUDIO = BASE + "audio/";

  /** Script assets directory. */
  public static final String SCRIPTS = BASE + "scripts/";

  /** Font assets directory. */
  public static final String FONTS = BASE + "fonts/";

  /** UI assets directory. */
  public static final String UI = BASE + "ui/";

  /** Video assets directory. */
  public static final String VIDEO = BASE + "video/";

  /** Configuration assets directory. */
  public static final String CONFIG = BASE + "config/";

  /**
   * Map an {@link AssetType} to its conventional directory path.
   *
   * @param type the asset type
   * @return the directory path string (always ends with {@code /})
   */
  public static String forType(AssetType type) {
    return switch (type) {
      case IMAGE -> IMAGES;
      case AUDIO -> AUDIO;
      case SCRIPT -> SCRIPTS;
      case FONT -> FONTS;
      case UI -> UI;
      case VIDEO -> VIDEO;
      case CONFIG -> CONFIG;
      case OTHER -> BASE;
    };
  }

  /**
   * Build a full asset path by combining the type's directory with a
   * relative asset name. Leading slashes on {@code name} are stripped.
   *
   * @param type the asset type
   * @param name the relative asset name (e.g. "characters/hero.png")
   * @return the fully-qualified asset path
   */
  public static String build(AssetType type, String name) {
    String dir = forType(type);
    if (name.startsWith("/")) name = name.substring(1);
    return dir + name;
  }
}
