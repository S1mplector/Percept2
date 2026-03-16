package com.jvn.editor.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

final class StoryboardOverlayCatalog {
  private static final int MAX_SCAN_DEPTH = 10;
  private static final int DISCOVERY_DEPTH = 4;
  private static final List<String> STORYBOARD_TOKENS =
      List.of("storyboard", "storyboards", "animatic", "boards", "board", "shots", "shot");
  private static final List<String> COMMON_ROOTS =
      List.of(
          "storyboard",
          "storyboards",
          "boards",
          "board",
          "animatic",
          "reference/storyboards",
          "references/storyboards",
          "reference/storyboard",
          "references/storyboard",
          "docs/storyboards",
          "docs/storyboard",
          "assets/storyboards",
          "assets/storyboard");

  private StoryboardOverlayCatalog() {
  }

  static ScanResult scan(Path projectRoot, String folderOverride) {
    if (projectRoot == null || !Files.isDirectory(projectRoot)) {
      return new ScanResult("No project loaded", "Open a project to browse storyboard frames.", List.of());
    }
    Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
    String override = folderOverride == null ? "" : folderOverride.trim();
    if (!override.isBlank()) {
      Path explicit = resolveOverride(normalizedRoot, override);
      if (explicit == null || !Files.isDirectory(explicit)) {
        return new ScanResult(
            "Manual folder",
            "Storyboard folder not found: " + override,
            List.of());
      }
      List<Path> frames = collectImages(List.of(explicit));
      String label = "Manual folder: " + displayPath(normalizedRoot, explicit);
      String status = frames.isEmpty()
          ? "No storyboard frames found in " + displayPath(normalizedRoot, explicit) + "."
          : "Loaded " + frames.size() + " storyboard frame" + (frames.size() == 1 ? "" : "s") + ".";
      return new ScanResult(label, status, frames);
    }

    List<Path> preferredRoots = discoverStoryboardRoots(normalizedRoot);
    List<Path> preferredFrames = collectImages(preferredRoots);
    if (!preferredFrames.isEmpty()) {
      String label;
      if (preferredRoots.size() == 1) {
        label = "Auto: " + displayPath(normalizedRoot, preferredRoots.get(0));
      } else {
        label = "Auto-detected storyboard folders";
      }
      String status =
          "Loaded " + preferredFrames.size() + " storyboard frame"
              + (preferredFrames.size() == 1 ? "" : "s")
              + ".";
      return new ScanResult(label, status, preferredFrames);
    }

    List<Path> fallback = collectImages(List.of(normalizedRoot));
    fallback.sort(Comparator
        .comparingInt(StoryboardOverlayCatalog::rankPath)
        .reversed()
        .thenComparing(path -> displayPath(normalizedRoot, path), String.CASE_INSENSITIVE_ORDER));
    if (fallback.isEmpty()) {
      return new ScanResult("Project images", "No image files found in the current project.", List.of());
    }
    return new ScanResult(
        "Project images",
        "No storyboard folder found. Showing project images instead.",
        fallback);
  }

  static String displayPath(Path projectRoot, Path path) {
    if (path == null) return "";
    Path normalized = path.toAbsolutePath().normalize();
    if (projectRoot != null) {
      Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
      if (normalized.startsWith(normalizedRoot)) {
        return normalizedRoot.relativize(normalized).toString().replace('\\', '/');
      }
    }
    return normalized.toString().replace('\\', '/');
  }

  private static Path resolveOverride(Path projectRoot, String folderOverride) {
    if (folderOverride == null || folderOverride.isBlank()) return null;
    try {
      Path path = Path.of(folderOverride.trim());
      if (!path.isAbsolute()) {
        path = projectRoot.resolve(path).normalize();
      }
      return path.toAbsolutePath().normalize();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static List<Path> discoverStoryboardRoots(Path projectRoot) {
    Set<Path> candidates = new LinkedHashSet<>();
    for (String commonRoot : COMMON_ROOTS) {
      Path candidate = projectRoot.resolve(commonRoot).normalize();
      if (Files.isDirectory(candidate)) candidates.add(candidate);
    }
    try (Stream<Path> stream = Files.walk(projectRoot, DISCOVERY_DEPTH)) {
      stream
          .filter(Files::isDirectory)
          .map(Path::toAbsolutePath)
          .map(Path::normalize)
          .filter(path -> !Objects.equals(path, projectRoot))
          .filter(StoryboardOverlayCatalog::looksLikeStoryboardDirectory)
          .sorted(Comparator
              .comparingInt(StoryboardOverlayCatalog::rankPath)
              .reversed()
              .thenComparingInt(StoryboardOverlayCatalog::depth)
              .thenComparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
          .forEach(candidates::add);
    } catch (IOException ignored) {
    }
    return new ArrayList<>(candidates);
  }

  private static List<Path> collectImages(List<Path> roots) {
    LinkedHashSet<Path> images = new LinkedHashSet<>();
    for (Path root : roots) {
      if (root == null || !Files.isDirectory(root)) continue;
      try (Stream<Path> stream = Files.walk(root, MAX_SCAN_DEPTH)) {
        stream
            .filter(Files::isRegularFile)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .filter(StoryboardOverlayCatalog::isImageFile)
            .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
            .forEach(images::add);
      } catch (IOException ignored) {
      }
    }
    return new ArrayList<>(images);
  }

  private static boolean looksLikeStoryboardDirectory(Path path) {
    if (path == null) return false;
    String normalized = normalizePath(path);
    for (String token : STORYBOARD_TOKENS) {
      if (normalized.contains("/" + token + "/") || normalized.endsWith("/" + token)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isImageFile(Path path) {
    if (path == null) return false;
    String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".png")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".webp");
  }

  private static int rankPath(Path path) {
    String normalized = normalizePath(path);
    int score = 0;
    if (normalized.contains("storyboards")) score += 600;
    if (normalized.contains("storyboard")) score += 500;
    if (normalized.contains("animatic")) score += 350;
    if (normalized.contains("/boards/")) score += 260;
    if (normalized.endsWith("/boards")) score += 240;
    if (normalized.contains("/board/")) score += 200;
    if (normalized.endsWith("/board")) score += 180;
    if (normalized.contains("shot")) score += 90;
    if (normalized.contains("reference")) score += 45;
    if (normalized.contains("/ref/")) score += 20;
    return score;
  }

  private static String normalizePath(Path path) {
    return path == null
        ? ""
        : path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
  }

  private static int depth(Path path) {
    return path == null ? Integer.MAX_VALUE : path.toAbsolutePath().normalize().getNameCount();
  }

  record ScanResult(String sourceLabel, String statusMessage, List<Path> frames) {
  }
}
