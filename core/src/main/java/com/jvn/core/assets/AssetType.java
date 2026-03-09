package com.jvn.core.assets;

/**
 * Classification of game assets by their purpose/media type.
 *
 * <p>Each constant maps to a conventional subdirectory under the
 * {@link AssetPaths#BASE} root (e.g. {@code game/images/},
 * {@code game/audio/}). The engine and editor use this enum to
 * resolve asset paths, populate catalogues, and filter file lists.</p>
 *
 * @see AssetPaths#forType(AssetType)
 * @see AssetManager
 */
public enum AssetType {
  /** Raster images: backgrounds, sprites, UI textures (.png, .jpg, .webp). */
  IMAGE,
  /** Audio files: music, sound effects, voice lines (.wav, .ogg, .mp3). */
  AUDIO,
  /** Script files: VNS dialogue scripts, JES game scripts (.vns, .jes). */
  SCRIPT,
  /** Font files: TTF / OTF typefaces for text rendering. */
  FONT,
  /** UI assets: nine-patch panels, icons, button skins. */
  UI,
  /** Video files: cutscenes, animated backgrounds (.mp4, .webm). */
  VIDEO,
  /** Configuration files: settings, profiles, registry files (.properties). */
  CONFIG,
  /** Uncategorised / project-specific assets. */
  OTHER
}
