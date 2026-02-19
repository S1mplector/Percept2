package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * AssetManager backed by a filesystem root, for loading external asset packs.
 */
public class FilesystemAssetManager implements AssetManager {
  private final Path root;

  public FilesystemAssetManager(Path root) {
    this.root = root == null ? Paths.get(".") : root;
  }

  @Override
  public boolean exists(AssetType type, String name) {
    return Files.exists(resolve(type, name));
  }

  @Override
  public URL url(AssetType type, String name) {
    try {
      Path p = resolve(type, name);
      if (!Files.exists(p)) return null;
      return p.toUri().toURL();
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    Path p = resolve(type, name);
    return Files.newInputStream(p);
  }

  @Override
  public List<String> list(String directory) {
    Path dir = root.resolve(directory);
    if (!Files.isDirectory(dir)) return List.of();
    try (var stream = Files.list(dir)) {
      List<String> names = new ArrayList<>();
      stream.forEach(p -> names.add(p.getFileName().toString()));
      return names;
    } catch (IOException e) {
      return List.of();
    }
  }

  private Path resolve(AssetType type, String name) {
    String normalized = normalize(name);
    Path mapped = root.resolve(AssetPaths.build(type, normalized));
    if (Files.exists(mapped)) return mapped;

    // Allow direct project-relative paths (e.g. scripts/story/prologue.vns, assets/backgrounds/bg.png).
    if (!normalized.isEmpty()) {
      Path direct = root.resolve(normalized);
      if (Files.exists(direct)) return direct;
    }

    return switch (type) {
      case SCRIPT -> resolveScriptFallback(normalized, mapped);
      case IMAGE -> resolveImageFallback(normalized, mapped);
      case AUDIO -> resolveAudioFallback(normalized, mapped);
      case FONT -> resolveFontFallback(normalized, mapped);
      default -> mapped;
    };
  }

  private Path resolveScriptFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/scripts/", "scripts/");
    Path p = root.resolve("scripts").resolve(rel);
    return Files.exists(p) ? p : fallback;
  }

  private Path resolveImageFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/images/", "images/", "assets/");
    Path p = root.resolve("assets").resolve(rel);
    if (Files.exists(p)) return p;
    Path images = root.resolve("assets/images").resolve(rel);
    return Files.exists(images) ? images : fallback;
  }

  private Path resolveAudioFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/audio/", "audio/", "assets/audio/");
    Path p = root.resolve("assets/audio").resolve(rel);
    if (Files.exists(p)) return p;
    Path audio = root.resolve("audio").resolve(rel);
    return Files.exists(audio) ? audio : fallback;
  }

  private Path resolveFontFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/fonts/", "fonts/", "assets/fonts/");
    Path p = root.resolve("assets/fonts").resolve(rel);
    if (Files.exists(p)) return p;
    Path fonts = root.resolve("fonts").resolve(rel);
    return Files.exists(fonts) ? fonts : fallback;
  }

  private String normalize(String name) {
    if (name == null) return "";
    String n = name.replace('\\', '/');
    while (n.startsWith("/")) n = n.substring(1);
    return n;
  }

  private String stripKnownPrefix(String value, String... prefixes) {
    if (value == null || value.isEmpty()) return "";
    for (String prefix : prefixes) {
      if (prefix != null && !prefix.isEmpty() && value.startsWith(prefix)) {
        return value.substring(prefix.length());
      }
    }
    return value;
  }
}
