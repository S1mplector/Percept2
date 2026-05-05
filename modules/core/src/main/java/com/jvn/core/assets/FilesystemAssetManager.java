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
 * {@link AssetManager} implementation that resolves assets from an external
 * filesystem directory tree.
 *
 * <p>This backend is used by the editor and runtime when assets live outside
 * the classpath — for example, a user's project directory or an external
 * asset pack. It tries the standard {@link AssetPaths} layout first, then
 * falls back to common alternative directory structures (e.g.
 * {@code assets/images/}, {@code scripts/}).</p>
 *
 * <h2>Resolution Order</h2>
 * <ol>
 *   <li>{@code root/game/<type>/<name>} — standard layout.</li>
 *   <li>{@code root/<name>} — direct project-relative path.</li>
 *   <li>Type-specific fallbacks (e.g. {@code root/scripts/},
 *       {@code root/assets/images/}).</li>
 * </ol>
 *
 * @see ClasspathAssetManager
 * @see OverlayAssetManager
 */
public class FilesystemAssetManager implements AssetManager {

  /** Root directory that all asset paths are resolved relative to. */
  private final Path root;

  /**
   * Construct a filesystem asset manager rooted at the given directory.
   *
   * @param root the project/asset root; if {@code null}, defaults to {@code "."}
   */
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

  /**
   * Resolve an asset path through the standard layout, direct path, and
   * type-specific fallback directories.
   */
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

  /** Try {@code root/scripts/<name>} as a fallback for script assets. */
  private Path resolveScriptFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/scripts/", "scripts/");
    Path p = root.resolve("scripts").resolve(rel);
    return Files.exists(p) ? p : fallback;
  }

  /** Try {@code root/assets/<name>} and {@code root/assets/images/<name>} fallbacks. */
  private Path resolveImageFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/images/", "images/", "assets/");
    Path p = root.resolve("assets").resolve(rel);
    if (Files.exists(p)) return p;
    Path images = root.resolve("assets/images").resolve(rel);
    return Files.exists(images) ? images : fallback;
  }

  /** Try {@code root/assets/audio/<name>} and {@code root/audio/<name>} fallbacks. */
  private Path resolveAudioFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/audio/", "audio/", "assets/audio/");
    Path p = root.resolve("assets/audio").resolve(rel);
    if (Files.exists(p)) return p;
    Path audio = root.resolve("audio").resolve(rel);
    return Files.exists(audio) ? audio : fallback;
  }

  /** Try {@code root/assets/fonts/<name>} and {@code root/fonts/<name>} fallbacks. */
  private Path resolveFontFallback(String normalized, Path fallback) {
    String rel = stripKnownPrefix(normalized, "game/fonts/", "fonts/", "assets/fonts/");
    Path p = root.resolve("assets/fonts").resolve(rel);
    if (Files.exists(p)) return p;
    Path fonts = root.resolve("fonts").resolve(rel);
    return Files.exists(fonts) ? fonts : fallback;
  }

  /** Normalise a path: replace backslashes, strip leading slashes. */
  private String normalize(String name) {
    if (name == null) return "";
    String n = name.replace('\\', '/');
    while (n.startsWith("/")) n = n.substring(1);
    return n;
  }

  /** Strip the first matching known prefix from the value. */
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
