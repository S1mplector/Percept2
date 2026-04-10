package com.jvn.core.assets;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Shared audio-path resolution helpers used by runtime backends.
 *
 * <p>The engine accepts a mix of project-relative paths, standard asset IDs,
 * and direct filesystem paths. This helper normalizes those inputs so both
 * audio backends resolve the same candidates in the same order.</p>
 */
public final class AudioAssetResolver {
  private AudioAssetResolver() {
  }

  public static File resolveFile(File projectRoot, String id) {
    if (id == null || id.isBlank()) return null;

    File direct = asExistingFile(id);
    if (direct != null) return direct;

    String normalized = normalize(id);
    File directNormalized = asExistingFile(normalized);
    if (directNormalized != null) return directNormalized;

    if (projectRoot != null) {
      File projectDirect = asExistingFile(new File(projectRoot, normalized));
      if (projectDirect != null) return projectDirect;

      String projectName = projectRoot.getName();
      if (!projectName.isBlank() && normalized.startsWith(projectName + "/")) {
        File strippedProject = asExistingFile(new File(projectRoot, normalized.substring(projectName.length() + 1)));
        if (strippedProject != null) return strippedProject;
      }
    }

    try {
      String cwdName = new File(System.getProperty("user.dir", ".")).getName();
      if (!cwdName.isBlank() && normalized.startsWith(cwdName + "/")) {
        File strippedCwd = asExistingFile(normalized.substring(cwdName.length() + 1));
        if (strippedCwd != null) return strippedCwd;
      }
    } catch (Exception ignored) {
    }

    for (String candidate : candidatePaths(id)) {
      File candidateFile = asExistingFile(candidate);
      if (candidateFile != null) return candidateFile;
      if (projectRoot != null) {
        File projectCandidate = asExistingFile(new File(projectRoot, candidate));
        if (projectCandidate != null) return projectCandidate;
      }
    }
    return null;
  }

  public static URL resolveClasspathUrl(ClassLoader classLoader, String id) {
    if (classLoader == null || id == null || id.isBlank()) return null;
    for (String candidate : candidatePaths(id)) {
      URL url = classLoader.getResource(candidate);
      if (url != null) return url;
    }
    return null;
  }

  static List<String> candidatePaths(String id) {
    String normalized = normalize(id);
    String relative = stripKnownPrefix(normalized, "game/audio/", "audio/", "assets/audio/", "assets/demo/audio/");
    String assetId = relative.isBlank() ? normalized : relative;

    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    addCandidate(candidates, normalized);
    addCandidate(candidates, assetId);
    addCandidate(candidates, AssetPaths.build(AssetType.AUDIO, assetId));
    addCandidate(candidates, "audio/" + assetId);
    addCandidate(candidates, "assets/audio/" + assetId);
    addCandidate(candidates, "assets/demo/audio/" + assetId);
    addCandidate(candidates, "game/audio/" + assetId);
    return new ArrayList<>(candidates);
  }

  private static File asExistingFile(String path) {
    if (path == null || path.isBlank()) return null;
    File file = new File(path);
    return file.exists() && file.isFile() ? file : null;
  }

  private static File asExistingFile(File file) {
    return file != null && file.exists() && file.isFile() ? file : null;
  }

  private static void addCandidate(LinkedHashSet<String> candidates, String value) {
    if (value == null || value.isBlank()) return;
    candidates.add(normalize(value));
  }

  private static String normalize(String value) {
    if (value == null) return "";
    String normalized = value.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }

  private static String stripKnownPrefix(String value, String... prefixes) {
    if (value == null || value.isBlank()) return "";
    for (String prefix : prefixes) {
      if (prefix != null && !prefix.isBlank() && value.startsWith(prefix)) {
        return value.substring(prefix.length());
      }
    }
    return value;
  }
}
