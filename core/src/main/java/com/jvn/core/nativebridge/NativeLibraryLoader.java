package com.jvn.core.nativebridge;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class NativeLibraryLoader {
  private NativeLibraryLoader() {}

  public static boolean load(String baseName) {
    String explicit = System.getProperty("jvn.native.path");
    if (explicit != null && !explicit.isBlank()) {
      if (tryLoad(explicit)) return true;
    }
    try {
      System.loadLibrary(baseName);
      return true;
    } catch (UnsatisfiedLinkError | SecurityException ignored) {
    }

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

    for (Path candidate : candidates) {
      if (Files.exists(candidate) && tryLoad(candidate.toAbsolutePath().toString())) return true;
    }

    return false;
  }

  private static boolean tryLoad(String path) {
    try {
      System.load(path);
      return true;
    } catch (UnsatisfiedLinkError | SecurityException ignored) {
      return false;
    }
  }
}
