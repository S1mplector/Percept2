package com.jvn.core.nativebridge;

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
    Path local = Paths.get("native", mapped);
    if (Files.exists(local) && tryLoad(local.toAbsolutePath().toString())) return true;

    String os = System.getProperty("os.name", "").toLowerCase();
    String osDir = os.contains("mac") ? "mac" : (os.contains("win") ? "windows" : "linux");
    Path platform = Paths.get("native", osDir, mapped);
    if (Files.exists(platform) && tryLoad(platform.toAbsolutePath().toString())) return true;

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
