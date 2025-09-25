package com.jvn.core.assets;

public final class AssetPaths {
  private AssetPaths() {}

  public static final String BASE = "game/";
  public static final String IMAGES = BASE + "images/";
  public static final String AUDIO = BASE + "audio/";
  public static final String SCRIPTS = BASE + "scripts/";
  public static final String FONTS = BASE + "fonts/";

  public static String forType(AssetType type) {
    return switch (type) {
      case IMAGE -> IMAGES;
      case AUDIO -> AUDIO;
      case SCRIPT -> SCRIPTS;
      case FONT -> FONTS;
      case OTHER -> BASE;
    };
  }

  public static String build(AssetType type, String name) {
    String dir = forType(type);
    if (name.startsWith("/")) name = name.substring(1);
    return dir + name;
  }
}
