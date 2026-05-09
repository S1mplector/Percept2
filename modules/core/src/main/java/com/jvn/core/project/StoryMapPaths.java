package com.jvn.core.project;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/**
 * Shared path policy for the story arc/link DSL.
 * <p>
 * "Timeline" is reserved for Puppeteer/JES animation timelines. The story arc
 * graph is now called the Story Map, while older timeline project files remain
 * readable for existing projects.
 */
public final class StoryMapPaths {
  public static final String MANIFEST_KEY = "storyMap";
  public static final String LEGACY_MANIFEST_KEY = "timeline";
  public static final String DEFAULT_PATH = "config/story/story.storymap";
  public static final String LEGACY_CONFIG_TIMELINE_PATH = "config/timeline/story.timeline";
  public static final String LEGACY_STORY_DIR_TIMELINE_PATH = "story/story.timeline";
  public static final String LEGACY_ROOT_TIMELINE_PATH = "story.timeline";

  private StoryMapPaths() {
  }

  public static String configuredPath(Properties manifest) {
    String storyMap = property(manifest, MANIFEST_KEY);
    if (storyMap != null) return storyMap;
    return property(manifest, LEGACY_MANIFEST_KEY);
  }

  public static List<String> candidatePaths(Properties manifest) {
    LinkedHashSet<String> paths = new LinkedHashSet<>();
    add(paths, configuredPath(manifest));
    add(paths, DEFAULT_PATH);
    add(paths, LEGACY_CONFIG_TIMELINE_PATH);
    add(paths, LEGACY_STORY_DIR_TIMELINE_PATH);
    add(paths, LEGACY_ROOT_TIMELINE_PATH);
    return new ArrayList<>(paths);
  }

  public static File resolveExistingOrDefault(File projectRoot, Properties manifest) {
    if (projectRoot == null) return null;
    for (String candidate : candidatePaths(manifest)) {
      File file = new File(projectRoot, candidate);
      if (file.isFile()) return file;
    }
    String configured = configuredPath(manifest);
    return new File(projectRoot, configured == null ? DEFAULT_PATH : configured);
  }

  public static File resolveForProjectRoot(File projectRoot) {
    return resolveExistingOrDefault(projectRoot, loadManifest(projectRoot));
  }

  private static void add(LinkedHashSet<String> paths, String path) {
    if (path != null && !path.isBlank()) {
      paths.add(path.trim().replace('\\', '/'));
    }
  }

  private static String property(Properties manifest, String key) {
    if (manifest == null || key == null) return null;
    String value = manifest.getProperty(key);
    if (value == null || value.isBlank()) return null;
    return value.trim().replace('\\', '/');
  }

  private static Properties loadManifest(File projectRoot) {
    if (projectRoot == null) return null;
    File manifestFile = new File(projectRoot, "jvn.project");
    if (!manifestFile.isFile()) return null;
    try (FileInputStream in = new FileInputStream(manifestFile)) {
      Properties manifest = new Properties();
      manifest.load(in);
      return manifest;
    } catch (Exception ignored) {
      return null;
    }
  }
}
