package com.jvn.core.nativebridge;

import java.nio.file.Path;

/**
 * Minimal JNI bridge for save-path file I/O primitives.
 *
 * <p>Current scope is intentionally narrow: we only expose atomic-write
 * for VN save payloads, and always keep Java fallback behavior.</p>
 */
public final class NativeIoBridge {
  private static final String LIB_NAME = "jvn_native_bridge";
  private static final boolean LOADED = NativeLibraryLoader.load(LIB_NAME);

  private NativeIoBridge() {}

  public static boolean isAvailable() {
    return LOADED;
  }

  public static boolean atomicWrite(Path targetPath, byte[] data, boolean fsyncFile, boolean fsyncDir) {
    if (!LOADED || targetPath == null || data == null) return false;
    try {
      return atomicWriteNative(targetPath.toAbsolutePath().toString(), data, fsyncFile, fsyncDir);
    } catch (UnsatisfiedLinkError | SecurityException ex) {
      return false;
    }
  }

  private static native boolean atomicWriteNative(String targetPath, byte[] data, boolean fsyncFile, boolean fsyncDir);
}
