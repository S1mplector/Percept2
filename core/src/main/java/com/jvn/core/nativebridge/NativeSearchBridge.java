package com.jvn.core.nativebridge;

import java.util.Arrays;
import java.util.Locale;

/**
 * Native-accelerated text search bridge used by JVN editors and script tools.
 *
 * <p>Falls back to pure Java when native library is unavailable, when the JVM
 * blocks native loading, or when input contains non-ASCII content.</p>
 */
public final class NativeSearchBridge {
  private static final String LIB_NAME = "jvn_native_bridge";
  private static final boolean LOADED = NativeLibraryLoader.load(LIB_NAME);

  private NativeSearchBridge() {}

  public static boolean isAvailable() {
    return LOADED;
  }

  public static int findCaseInsensitive(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) return -1;
    if (needle.length() > haystack.length()) return -1;
    if (!LOADED || !isAscii(haystack) || !isAscii(needle)) {
      return findCaseInsensitiveJava(haystack, needle);
    }
    try {
      return findCaseInsensitiveNative(haystack, needle);
    } catch (UnsatisfiedLinkError | SecurityException e) {
      return findCaseInsensitiveJava(haystack, needle);
    }
  }

  public static int countCaseInsensitive(String haystack, String needle) {
    if (haystack == null || needle == null || needle.isEmpty()) return 0;
    if (needle.length() > haystack.length()) return 0;
    if (!LOADED || !isAscii(haystack) || !isAscii(needle)) {
      return countCaseInsensitiveJava(haystack, needle);
    }
    try {
      return countCaseInsensitiveNative(haystack, needle);
    } catch (UnsatisfiedLinkError | SecurityException e) {
      return countCaseInsensitiveJava(haystack, needle);
    }
  }

  public static int[] findAllCaseInsensitive(String haystack, String needle) {
    if (haystack == null || needle == null) return new int[0];
    return findAllCaseInsensitive(haystack, needle, haystack.length());
  }

  public static int[] findAllCaseInsensitive(String haystack, String needle, int maxResults) {
    if (haystack == null || needle == null || needle.isEmpty() || maxResults <= 0) return new int[0];
    if (needle.length() > haystack.length()) return new int[0];

    int safeMax = Math.min(Math.max(0, maxResults), haystack.length());
    if (safeMax <= 0) return new int[0];

    if (!LOADED || !isAscii(haystack) || !isAscii(needle)) {
      return findAllCaseInsensitiveJava(haystack, needle, safeMax);
    }
    try {
      int[] nativeOut = findAllCaseInsensitiveNative(haystack, needle, safeMax);
      return nativeOut != null ? nativeOut : new int[0];
    } catch (UnsatisfiedLinkError | SecurityException e) {
      return findAllCaseInsensitiveJava(haystack, needle, safeMax);
    }
  }

  private static native int findCaseInsensitiveNative(String haystack, String needle);

  private static native int countCaseInsensitiveNative(String haystack, String needle);

  private static native int[] findAllCaseInsensitiveNative(String haystack, String needle, int maxResults);

  private static int findCaseInsensitiveJava(String haystack, String needle) {
    String h = haystack.toLowerCase(Locale.ROOT);
    String n = needle.toLowerCase(Locale.ROOT);
    return h.indexOf(n);
  }

  private static int countCaseInsensitiveJava(String haystack, String needle) {
    String h = haystack.toLowerCase(Locale.ROOT);
    String n = needle.toLowerCase(Locale.ROOT);
    int count = 0;
    int pos = 0;
    while (pos <= h.length() - n.length()) {
      int idx = h.indexOf(n, pos);
      if (idx < 0) break;
      count++;
      pos = idx + 1;
    }
    return count;
  }

  private static int[] findAllCaseInsensitiveJava(String haystack, String needle, int maxResults) {
    String h = haystack.toLowerCase(Locale.ROOT);
    String n = needle.toLowerCase(Locale.ROOT);
    int[] out = new int[Math.min(16, Math.max(1, maxResults))];
    int count = 0;
    int pos = 0;
    while (pos <= h.length() - n.length() && count < maxResults) {
      int idx = h.indexOf(n, pos);
      if (idx < 0) break;
      if (count == out.length) {
        out = Arrays.copyOf(out, Math.min(maxResults, out.length * 2));
      }
      out[count++] = idx;
      pos = idx + 1;
    }
    return Arrays.copyOf(out, count);
  }

  private static boolean isAscii(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) > 0x7F) return false;
    }
    return true;
  }
}

