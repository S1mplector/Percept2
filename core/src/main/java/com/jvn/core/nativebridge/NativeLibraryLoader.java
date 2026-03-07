package com.jvn.core.nativebridge;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public final class NativeLibraryLoader {
  private NativeLibraryLoader() {}

  public static boolean load(String baseName) {
    for (String explicit : explicitPaths(baseName)) {
      if (tryLoad(explicit)) return true;
    }
    try {
      System.loadLibrary(baseName);
      return true;
    } catch (UnsatisfiedLinkError | SecurityException ignored) {
    }

    for (Path candidate : candidatePaths(baseName)) {
      if (Files.exists(candidate) && tryLoad(candidate.toAbsolutePath().toString())) return true;
    }

    return false;
  }

  public static Path findExisting(String baseName) {
    for (String explicit : explicitPaths(baseName)) {
      if (explicit == null || explicit.isBlank()) continue;
      Path path = Paths.get(explicit);
      if (Files.exists(path)) return path.toAbsolutePath().normalize();
    }
    for (Path candidate : candidatePaths(baseName)) {
      if (Files.exists(candidate)) return candidate.toAbsolutePath().normalize();
    }
    return null;
  }

  public static List<Path> candidatePaths(String baseName) {
    String mapped = System.mapLibraryName(baseName);
    String os = System.getProperty("os.name", "").toLowerCase();
    String osDir = os.contains("mac") ? "mac" : (os.contains("win") ? "windows" : "linux");

    List<Path> candidates = new ArrayList<>();
    candidates.add(Paths.get("native", mapped));
    candidates.add(Paths.get("native", osDir, mapped));
    candidates.add(Paths.get("audio-fx", "build", "native", mapped));
    candidates.add(Paths.get("audio-fx", "build", "native", osDir, mapped));
    candidates.add(Paths.get("audio-fx", "build", "native", "Release", mapped));
    candidates.add(Paths.get("audio-fx", "build", "native", "Debug", mapped));
    candidates.add(Paths.get("audio-fx", "build", "native", osDir, "Release", mapped));
    candidates.add(Paths.get("audio-fx", "build", "native", osDir, "Debug", mapped));
    candidates.add(Paths.get("native-math", "build", mapped));
    candidates.add(Paths.get("native-math", "build", "Release", mapped));
    candidates.add(Paths.get("native-math", "build", "Debug", mapped));
    candidates.add(Paths.get("native-math", "build", osDir, mapped));
    candidates.add(Paths.get("native-math", "build", osDir, "Release", mapped));
    candidates.add(Paths.get("native-math", "build", osDir, "Debug", mapped));
    return candidates;
  }

  private static boolean tryLoad(String path) {
    try {
      System.load(path);
      return true;
    } catch (UnsatisfiedLinkError | SecurityException ignored) {
      return false;
    }
  }

  private static List<String> explicitPaths(String baseName) {
    Set<String> paths = new LinkedHashSet<>();
    addPropertyPaths(paths, System.getProperty("jvn.native.path." + baseName));
    addPropertyPaths(paths, System.getProperty("jvn.native.path"));
    return new ArrayList<>(paths);
  }

  private static void addPropertyPaths(Set<String> out, String raw) {
    if (raw == null || raw.isBlank()) return;
    String[] parts = raw.split(java.util.regex.Pattern.quote(File.pathSeparator));
    for (String part : parts) {
      if (part == null) continue;
      String trimmed = part.trim();
      if (!trimmed.isBlank()) out.add(trimmed);
    }
  }
}
