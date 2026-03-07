package com.jvn.core.nativebridge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLibraryLoaderTest {

  @Test
  void findExistingPrefersBaseSpecificProperty() throws Exception {
    Path specific = Files.createTempFile("jvn-audiofx-native-", ".dylib");
    Path generic = Files.createTempFile("jvn-generic-native-", ".dylib");
    String key = "jvn.native.path.jvn_audiofx_native";
    String genericKey = "jvn.native.path";
    String previousSpecific = System.getProperty(key);
    String previousGeneric = System.getProperty(genericKey);
    try {
      System.setProperty(key, specific.toString());
      System.setProperty(genericKey, generic.toString());
      Path resolved = NativeLibraryLoader.findExisting("jvn_audiofx_native");
      assertEquals(specific.toAbsolutePath().normalize(), resolved);
    } finally {
      restoreProperty(key, previousSpecific);
      restoreProperty(genericKey, previousGeneric);
      Files.deleteIfExists(specific);
      Files.deleteIfExists(generic);
    }
  }

  @Test
  void findExistingSupportsMultipleGenericPaths() throws Exception {
    Path missing = Path.of("build/does-not-exist/libmissing.dylib");
    Path present = Files.createTempFile("jvn-generic-native-", ".dylib");
    String genericKey = "jvn.native.path";
    String previousGeneric = System.getProperty(genericKey);
    try {
      System.setProperty(genericKey, missing + java.io.File.pathSeparator + present);
      Path resolved = NativeLibraryLoader.findExisting("jvn_audiofx_native");
      assertTrue(resolved != null && resolved.endsWith(present.getFileName()));
      assertEquals(present.toAbsolutePath().normalize(), resolved);
    } finally {
      restoreProperty(genericKey, previousGeneric);
      Files.deleteIfExists(present);
    }
  }

  private static void restoreProperty(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
