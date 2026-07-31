package com.jvn.editor.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/** Loads project-tree artwork from the active freedesktop icon theme on Linux. */
final class FreedesktopProjectIconPack {
  private static final Pattern SIZE_COMPONENT = Pattern.compile("^(\\d+)(?:x\\d+)?(?:@\\d+)?$");
  private static final Map<Path, Map<String, List<Path>>> THEME_INDEXES = new ConcurrentHashMap<>();
  private static final Map<IconRequest, Optional<Path>> PATH_CACHE = new ConcurrentHashMap<>();
  private static final Map<ImageRequest, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
  private static final List<Path> ICON_ROOTS = iconRoots();
  private static final String ACTIVE_THEME = detectActiveTheme();
  private static final List<Path> ACTIVE_THEME_CHAIN = themeChain(ICON_ROOTS, ACTIVE_THEME);

  private FreedesktopProjectIconPack() {}

  static Optional<Region> icon(List<String> semanticNames, double requestedSize) {
    if (!isLinux() || semanticNames == null || semanticNames.isEmpty()) return Optional.empty();
    int size = Math.max(12, (int) Math.round(requestedSize > 0 ? requestedSize : 18.0));
    IconRequest request = new IconRequest(List.copyOf(semanticNames), size);
    Optional<Path> path = PATH_CACHE.computeIfAbsent(
        request,
        key -> resolveIconPathInThemes(ACTIVE_THEME_CHAIN, key.names(), key.size()));
    if (path.isEmpty()) return Optional.empty();

    ImageRequest imageRequest = new ImageRequest(path.get(), size);
    Image image = IMAGE_CACHE.computeIfAbsent(imageRequest, key -> new Image(
        key.path().toUri().toString(),
        key.size(),
        key.size(),
        true,
        true,
        false));
    if (image.isError()) return Optional.empty();

    ImageView view = new ImageView(image);
    view.setFitWidth(size);
    view.setFitHeight(size);
    view.setPreserveRatio(true);
    view.setSmooth(true);
    view.setMouseTransparent(true);

    StackPane holder = new StackPane(view);
    holder.setAlignment(Pos.CENTER);
    holder.setMinSize(size, size);
    holder.setPrefSize(size, size);
    holder.setMaxSize(size, size);
    holder.setMouseTransparent(true);
    holder.getStyleClass().add("project-system-icon");
    return Optional.of(holder);
  }

  static Optional<Path> resolveIconPath(
      List<Path> iconRoots,
      String requestedTheme,
      List<String> semanticNames,
      int requestedSize
  ) {
    if (iconRoots == null || semanticNames == null || semanticNames.isEmpty()) return Optional.empty();
    List<String> names = semanticNames.stream()
        .filter(name -> name != null && !name.isBlank())
        .map(name -> name.trim().toLowerCase(Locale.ROOT))
        .distinct()
        .toList();
    if (names.isEmpty()) return Optional.empty();

    return resolveIconPathInThemes(themeChain(iconRoots, requestedTheme), names, requestedSize);
  }

  private static Optional<Path> resolveIconPathInThemes(
      List<Path> themeDirectories,
      List<String> names,
      int requestedSize
  ) {
    for (Path themeDirectory : themeDirectories) {
      Map<String, List<Path>> index = THEME_INDEXES.computeIfAbsent(
          themeDirectory,
          FreedesktopProjectIconPack::indexTheme);
      for (String name : names) {
        List<Path> matches = index.getOrDefault(name, List.of());
        Optional<Path> closest = matches.stream()
            .min(Comparator
                .comparingInt((Path path) -> Math.abs(iconSize(path, requestedSize) - requestedSize))
                .thenComparingInt(path -> contextPenalty(path, name))
                .thenComparing(Path::toString));
        if (closest.isPresent()) return closest;
      }
    }
    return Optional.empty();
  }

  static String parseThemeSetting(String content) {
    if (content == null || content.isBlank()) return "";
    for (String line : content.split("\\R")) {
      String trimmed = line.trim();
      int separator = trimmed.indexOf('=');
      if (separator <= 0) continue;
      String key = trimmed.substring(0, separator).trim();
      if (!"gtk-icon-theme-name".equalsIgnoreCase(key)) continue;
      return unquote(trimmed.substring(separator + 1));
    }
    return "";
  }

  static String activeThemeName() {
    return ACTIVE_THEME;
  }

  private static List<Path> themeChain(List<Path> roots, String requestedTheme) {
    LinkedHashSet<Path> directories = new LinkedHashSet<>();
    LinkedHashSet<String> visited = new LinkedHashSet<>();
    collectTheme(roots, normalizeTheme(requestedTheme), directories, visited);
    collectTheme(roots, "Adwaita", directories, visited);
    collectTheme(roots, "hicolor", directories, visited);
    return List.copyOf(directories);
  }

  private static void collectTheme(
      List<Path> roots,
      String theme,
      Set<Path> directories,
      Set<String> visited
  ) {
    if (theme.isBlank() || !visited.add(theme.toLowerCase(Locale.ROOT))) return;
    List<Path> matches = roots.stream()
        .map(root -> root.resolve(theme))
        .filter(Files::isDirectory)
        .toList();
    directories.addAll(matches);
    for (Path directory : matches) {
      for (String inherited : inheritedThemes(directory.resolve("index.theme"))) {
        collectTheme(roots, inherited, directories, visited);
      }
    }
  }

  private static List<String> inheritedThemes(Path indexFile) {
    if (!Files.isRegularFile(indexFile)) return List.of();
    try {
      for (String line : Files.readAllLines(indexFile, StandardCharsets.UTF_8)) {
        String trimmed = line.trim();
        if (!trimmed.regionMatches(true, 0, "Inherits=", 0, "Inherits=".length())) continue;
        return Arrays.stream(trimmed.substring("Inherits=".length()).split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
      }
    } catch (IOException ignored) {
      // Unreadable system theme metadata simply falls through to known fallback themes.
    }
    return List.of();
  }

  private static Map<String, List<Path>> indexTheme(Path themeDirectory) {
    Map<String, List<Path>> icons = new ConcurrentHashMap<>();
    if (!Files.isDirectory(themeDirectory)) return icons;
    try (var paths = Files.walk(themeDirectory, 5)) {
      // JavaFX Image decodes raster artwork directly; SVG-only entries use the bundled fallback pack.
      paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
          .forEach(path -> icons
              .computeIfAbsent(baseName(path), ignored -> new ArrayList<>())
              .add(path));
    } catch (IOException ignored) {
      // A partially unreadable theme is allowed; available inherited icons still work.
    }
    icons.replaceAll((ignored, values) -> List.copyOf(values));
    return icons;
  }

  private static int iconSize(Path path, int fallback) {
    for (Path part : path) {
      Matcher matcher = SIZE_COMPONENT.matcher(part.toString().toLowerCase(Locale.ROOT));
      if (matcher.matches()) {
        try {
          return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
          return fallback;
        }
      }
    }
    return fallback;
  }

  private static int contextPenalty(Path path, String name) {
    String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    if (name.startsWith("folder") || name.startsWith("user-")) {
      return normalized.contains("/places/") ? 0 : 1;
    }
    return normalized.contains("/mimetypes/") || normalized.contains("/mimes/") ? 0 : 1;
  }

  private static String baseName(Path path) {
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static List<Path> iconRoots() {
    LinkedHashSet<Path> roots = new LinkedHashSet<>();
    Path home = Paths.get(System.getProperty("user.home", "."));
    roots.add(home.resolve(".icons"));
    String dataHome = System.getenv("XDG_DATA_HOME");
    roots.add((dataHome == null || dataHome.isBlank()
        ? home.resolve(".local/share")
        : Paths.get(dataHome)).resolve("icons"));
    String dataDirectories = System.getenv("XDG_DATA_DIRS");
    String resolved = dataDirectories == null || dataDirectories.isBlank()
        ? "/usr/local/share:/usr/share"
        : dataDirectories;
    for (String directory : resolved.split(Pattern.quote(java.io.File.pathSeparator))) {
      if (!directory.isBlank()) roots.add(Paths.get(directory).resolve("icons"));
    }
    roots.add(Paths.get("/usr/share/icons"));
    return roots.stream().filter(Files::isDirectory).toList();
  }

  private static String detectActiveTheme() {
    String override = firstNonBlank(System.getProperty("jvn.icon.theme"), System.getenv("JVN_ICON_THEME"));
    if (!override.isBlank()) return override;

    Path home = Paths.get(System.getProperty("user.home", "."));
    for (Path settings : List.of(
        home.resolve(".config/gtk-4.0/settings.ini"),
        home.resolve(".config/gtk-3.0/settings.ini"))) {
      try {
        String theme = parseThemeSetting(Files.readString(settings, StandardCharsets.UTF_8));
        if (!theme.isBlank()) return theme;
      } catch (IOException ignored) {
        // Try the next standard source.
      }
    }

    String gsettingsTheme = readGsettingsTheme();
    if (!gsettingsTheme.isBlank()) return gsettingsTheme;
    return "Adwaita";
  }

  private static String readGsettingsTheme() {
    if (!isLinux()) return "";
    Process process = null;
    try {
      process = new ProcessBuilder(
          "gsettings", "get", "org.gnome.desktop.interface", "icon-theme")
          .redirectErrorStream(true)
          .start();
      if (!process.waitFor(600, TimeUnit.MILLISECONDS)) {
        process.destroy();
        return "";
      }
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        return unquote(reader.readLine());
      }
    } catch (IOException ignored) {
      return "";
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
      return "";
    } finally {
      if (process != null && process.isAlive()) process.destroy();
    }
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first.trim();
    return second == null ? "" : second.trim();
  }

  private static String normalizeTheme(String theme) {
    String normalized = unquote(theme);
    return normalized.isBlank() ? "Adwaita" : normalized;
  }

  private static String unquote(String value) {
    if (value == null) return "";
    String normalized = value.trim();
    if (normalized.length() >= 2) {
      char first = normalized.charAt(0);
      char last = normalized.charAt(normalized.length() - 1);
      if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
        normalized = normalized.substring(1, normalized.length() - 1).trim();
      }
    }
    return normalized;
  }

  private static boolean isLinux() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
  }

  private record IconRequest(List<String> names, int size) {}

  private record ImageRequest(Path path, int size) {}
}
