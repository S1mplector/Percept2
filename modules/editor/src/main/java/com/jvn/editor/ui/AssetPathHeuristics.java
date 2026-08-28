package com.jvn.editor.ui;

import com.jvn.editor.ui.AssetAutoLabelService.AssetKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** File-type, path-taxonomy, and identifier rules shared by asset labeling components. */
final class AssetPathHeuristics {
  private static final Set<String> IMAGE_EXTENSIONS = Set.of(
      "png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "tif", "tiff");
  private static final Set<String> AUDIO_EXTENSIONS = Set.of(
      "ogg", "wav", "mp3", "flac", "aac", "m4a");
  private static final Set<String> VIDEO_EXTENSIONS = Set.of(
      "mp4", "webm", "mov", "avi", "mkv");
  private static final Set<String> FONT_EXTENSIONS = Set.of("ttf", "otf", "woff", "woff2");
  private static final Set<String> DATA_EXTENSIONS = Set.of(
      "json", "yaml", "yml", "toml", "xml", "properties", "csv", "tsv");

  private AssetPathHeuristics() {}

  static boolean isSupportedAsset(Path path) {
    if (path == null || !Files.isRegularFile(path)) return false;
    String extension = extension(path.getFileName().toString());
    return IMAGE_EXTENSIONS.contains(extension)
        || AUDIO_EXTENSIONS.contains(extension)
        || VIDEO_EXTENSIONS.contains(extension)
        || FONT_EXTENSIONS.contains(extension)
        || DATA_EXTENSIONS.contains(extension);
  }

  static AssetKind kindFromPath(Path path) {
    if (path == null) return AssetKind.UNKNOWN;
    String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    String ext = extension(path.getFileName() == null ? "" : path.getFileName().toString());
    if (AUDIO_EXTENSIONS.contains(ext)) return AssetKind.AUDIO;
    if (VIDEO_EXTENSIONS.contains(ext)) return AssetKind.VIDEO;
    if (FONT_EXTENSIONS.contains(ext)) return AssetKind.FONT;
    if (DATA_EXTENSIONS.contains(ext)) return AssetKind.DATA;
    if (!IMAGE_EXTENSIONS.contains(ext)) return AssetKind.UNKNOWN;
    if (containsSegment(normalized, "background") || containsSegment(normalized, "backgrounds")
        || containsSegment(normalized, "bg")) return AssetKind.BACKGROUND;
    if (containsSegment(normalized, "characters") || containsSegment(normalized, "character")
        || containsSegment(normalized, "lunch_table")) return AssetKind.CHARACTER_LAYER;
    if (containsSegment(normalized, "panels") || containsSegment(normalized, "panel")
        || normalized.contains("comic")) return AssetKind.PANEL;
    if (containsSegment(normalized, "ui") || containsSegment(normalized, "phone")
        || containsSegment(normalized, "menu") || containsSegment(normalized, "hud")) {
      return AssetKind.UI;
    }
    if (normalized.contains("/ui_") || normalized.contains("/text box/")
        || normalized.contains("/choices/") || normalized.contains("/pause_menu/")
        || normalized.contains("/phone assets/") || normalized.contains("/title screen")
        || normalized.contains("/quit/") || normalized.contains("/cursor")
        || normalized.endsWith("/end_of_text_icon.png")) {
      return AssetKind.UI;
    }
    if (containsSegment(normalized, "effects") || containsSegment(normalized, "effect")
        || containsSegment(normalized, "fx") || normalized.contains("particle")) {
      return AssetKind.EFFECT;
    }
    if (containsSegment(normalized, "props") || containsSegment(normalized, "prop")
        || containsSegment(normalized, "miscs") || containsSegment(normalized, "misc")
        || containsSegment(normalized, "items")) return AssetKind.PROP;
    String fileStem = sanitizeId(stem(path.getFileName() == null ? "" : path.getFileName().toString()));
    if (fileStem.matches("(?:bg|background)(?:_.*)?")
        || fileStem.matches(".*_(?:bg|background)")) return AssetKind.BACKGROUND;
    if (fileStem.matches("(?:panel|comic)(?:_.*)?")) return AssetKind.PANEL;
    if (fileStem.matches(".*(?:button|icon|menu|textbox|text_box|hud).*")
        || fileStem.startsWith("ui_")) return AssetKind.UI;
    if (fileStem.matches("(?:fx|effect|particle)(?:_.*)?")) return AssetKind.EFFECT;
    if (containsSegment(normalized, "sprites") || containsSegment(normalized, "portraits")) {
      return AssetKind.CHARACTER_SPRITE;
    }
    return AssetKind.UNKNOWN;
  }

  static String inferOwner(String relativePath, AssetKind kind) {
    List<String> parts = pathParts(relativePath);
    int characters = indexOf(parts, "characters", "character");
    if (characters >= 0 && characters + 1 < parts.size() - 1) {
      return sanitizeId(parts.get(characters + 1));
    }
    int lunch = indexOf(parts, "lunch_table");
    if (lunch >= 0 && lunch + 1 < parts.size() - 1) {
      return sanitizeId(parts.get(lunch + 1) + "_lunch");
    }
    if (kind == AssetKind.PANEL) {
      String name = Path.of(relativePath).getFileName().toString();
      Matcher matcher = Pattern.compile("(?i)(panel[_-]?[a-z0-9]+(?:[_-][a-z0-9]+)?)")
          .matcher(stem(name));
      return sanitizeId(matcher.find() ? matcher.group(1) : "panel_asset");
    }
    if (kind == AssetKind.PROP || kind == AssetKind.EFFECT || kind == AssetKind.UI) {
      return inferEntityId(relativePath, kind);
    }
    if (kind == AssetKind.CHARACTER_SPRITE || kind == AssetKind.CHARACTER_LAYER) {
      return sanitizeId(stem(Path.of(relativePath).getFileName().toString()));
    }
    return "";
  }

  static String inferEntityId(String relativePath, AssetKind kind) {
    String originalStem = stem(Path.of(relativePath).getFileName().toString());
    int separator = originalStem.lastIndexOf("_-_");
    if (separator < 0) separator = originalStem.lastIndexOf(" - ");
    String label = sanitizeId(separator > 0 ? originalStem.substring(0, separator) : originalStem);
    label = label.replaceFirst("^(assets?|images?|sprites?|props?|effects?|ui)_+", "");
    String[] tokens = label.split("_");
    if (tokens.length > 2 && tokens[tokens.length - 1].matches("\\d+")) {
      label = String.join("_", java.util.Arrays.copyOf(tokens, tokens.length - 1));
    }
    return label.isBlank() ? kind.name().toLowerCase(Locale.ROOT) + "_asset" : label;
  }

  static String inferLabel(String relativePath, AssetKind kind, String owner) {
    Path path = Path.of(relativePath);
    String originalStem = stem(path.getFileName().toString());
    String normalized = sanitizeId(originalStem);
    String ownerNormalized = sanitizeId(owner);
    normalized = stripApproximatePrefix(normalized, ownerNormalized);
    List<String> parts = pathParts(relativePath);
    int characterRoot = indexOf(parts, "characters", "character");
    if (characterRoot >= 0 && characterRoot + 1 < parts.size() - 1) {
      normalized = stripApproximatePrefix(
          normalized, sanitizeId(parts.get(characterRoot + 1)));
    }
    normalized = normalized.replaceFirst("^(assets?|images?|sprites?|props?)_+", "");
    normalized = normalized.replace("_-_", "_").replaceAll("_+", "_");

    String parent = parts.size() > 1 ? sanitizeId(parts.get(parts.size() - 2)) : "";
    String orientation = "";
    for (int i = Math.max(0, parts.size() - 5); i < parts.size() - 1; i++) {
      String value = sanitizeId(parts.get(i));
      if (value.contains("head_normal") || "normal".equals(value)) orientation = "normal";
      if (value.contains("head_side") || "side".equals(value)) orientation = "side";
      if (value.contains("head_tilted") || "tilted".equals(value)) orientation = "tilted";
    }
    String suffix = suffixAfterSeparator(originalStem);
    if (kind == AssetKind.CHARACTER_LAYER && !parent.isBlank()
        && Set.of("eyes", "mouth", "snoot", "snoots", "body", "additions", "addon")
            .contains(parent)) {
      String category = parent.endsWith("s") && !"eyes".equals(parent)
          ? parent.substring(0, parent.length() - 1) : parent;
      String semanticSuffix = sanitizeId(suffix);
      if (semanticSuffix.isBlank()) {
        int categoryIndex = normalized.indexOf(parent + "_");
        if (categoryIndex < 0) categoryIndex = normalized.indexOf(category + "_");
        if (categoryIndex >= 0) {
          int start = categoryIndex + (normalized.startsWith(parent, categoryIndex)
              ? parent.length() : category.length()) + 1;
          semanticSuffix = normalized.substring(Math.min(start, normalized.length()));
        }
      }
      semanticSuffix = semanticSuffix.replaceFirst("^" + Pattern.quote(parent) + "_+", "")
          .replaceFirst("^" + Pattern.quote(category) + "_+", "");
      StringBuilder semantic = new StringBuilder();
      if (!orientation.isBlank()) semantic.append(orientation).append('_');
      semantic.append(category);
      if (!semanticSuffix.isBlank() && !semanticSuffix.equals(category)) {
        semantic.append('_').append(semanticSuffix);
      }
      normalized = semantic.toString();
    }
    if (kind == AssetKind.BACKGROUND) {
      normalized = normalized.replaceFirst("^(?:bg|background)_+", "");
    }
    if ((kind == AssetKind.UI || kind == AssetKind.EFFECT) && normalized.matches("\\d+")) {
      normalized = "frame_" + normalized;
    }
    if (normalized.isBlank() && kind.isVnsDeclarable()) normalized = "default";
    return sanitizeId(normalized.isBlank()
        ? kind.name().toLowerCase(Locale.ROOT) + "_asset" : normalized);
  }

  static String uniqueLabel(String owner, String candidate, Set<String> used) {
    String base = sanitizeId(candidate);
    if (base.isBlank()) base = "asset";
    String value = base;
    int suffix = 2;
    while (used.contains(scopeKey(owner, value))) value = base + "_" + suffix++;
    return value;
  }

  static String recommendedDirectory(AssetKind kind, String owner) {
    return switch (kind) {
      case BACKGROUND -> "backgrounds";
      case CHARACTER_LAYER, CHARACTER_SPRITE -> owner == null || owner.isBlank()
          ? "characters/imported" : "characters/" + sanitizeId(owner);
      case PROP -> "props";
      case PANEL -> "panels";
      case UI -> "ui";
      case EFFECT -> "effects";
      case AUDIO -> "audio";
      case VIDEO -> "video";
      case FONT -> "fonts";
      case DATA -> "data";
      case UNKNOWN -> "import";
    };
  }

  static Path requireProjectRoot(Path root) throws IOException {
    Path normalized = root == null ? null : root.toAbsolutePath().normalize();
    if (normalized == null || !Files.isDirectory(normalized)) {
      throw new IOException("No project is open");
    }
    return normalized;
  }

  static String relative(Path root, Path file) {
    return normalizeRelative(root.relativize(file.toAbsolutePath().normalize()).toString());
  }

  static String normalizeRelative(String value) {
    if (value == null) return "";
    String normalized = value.strip().replace('\\', '/');
    while (normalized.startsWith("./")) normalized = normalized.substring(2);
    while (normalized.startsWith("/")) normalized = normalized.substring(1);
    return normalized;
  }

  static String parentPath(String path) {
    String normalized = normalizeRelative(path);
    int slash = normalized.lastIndexOf('/');
    return slash < 0 ? "" : normalized.substring(0, slash);
  }

  static List<String> pathParts(String path) {
    String[] raw = normalizeRelative(path).split("/");
    List<String> parts = new ArrayList<>();
    for (String value : raw) if (!value.isBlank()) parts.add(value);
    return parts;
  }

  static String sanitizeId(String value) {
    String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    normalized = normalized.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (normalized.isBlank()) return "";
    return Character.isDigit(normalized.charAt(0)) ? "asset_" + normalized : normalized;
  }

  static String scopeKey(String owner, String label) {
    return sanitizeId(owner) + "/" + sanitizeId(label);
  }

  private static String stripApproximatePrefix(String value, String prefix) {
    if (value == null || value.isBlank() || prefix == null || prefix.isBlank()) return value;
    if (value.equals(prefix)) return "";
    if (value.startsWith(prefix + "_")) return value.substring(prefix.length() + 1);
    String[] valueParts = value.split("_");
    String[] prefixParts = prefix.split("_");
    if (valueParts.length <= prefixParts.length) return value;
    for (int i = 0; i < prefixParts.length; i++) {
      String actual = valueParts[i];
      String expected = prefixParts[i];
      boolean close = actual.equals(expected)
          || (actual.endsWith(expected) && actual.length() - expected.length() <= 2)
          || (expected.endsWith(actual) && expected.length() - actual.length() <= 2);
      if (!close) return value;
    }
    return String.join("_", java.util.Arrays.copyOfRange(
        valueParts, prefixParts.length, valueParts.length));
  }

  private static int indexOf(List<String> parts, String... candidates) {
    Set<String> wanted = Set.of(candidates);
    for (int i = 0; i < parts.size(); i++) {
      if (wanted.contains(parts.get(i).toLowerCase(Locale.ROOT))) return i;
    }
    return -1;
  }

  private static boolean containsSegment(String path, String segment) {
    return ("/" + path + "/").contains("/" + segment.toLowerCase(Locale.ROOT) + "/");
  }

  private static String stem(String fileName) {
    int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName == null ? "" : fileName;
  }

  private static String extension(String fileName) {
    int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
    return dot >= 0 && dot + 1 < fileName.length()
        ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
  }

  private static String suffixAfterSeparator(String stem) {
    if (stem == null) return "";
    int separator = stem.lastIndexOf("_-_");
    if (separator < 0) separator = stem.lastIndexOf(" - ");
    return separator < 0 ? "" : stem.substring(separator + 3);
  }
}
