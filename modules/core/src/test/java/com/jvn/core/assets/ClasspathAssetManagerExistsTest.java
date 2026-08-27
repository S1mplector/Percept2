package com.jvn.core.assets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClasspathAssetManagerExistsTest {

  @Test
  void existsReturnsTrueForARealClasspathResource() {
    // modules/core/src/test/resources/game/audio/resolver-test.ogg is a real
    // file on modules/core's own test classpath (verified present on disk).
    // AssetPaths.build(AUDIO, "resolver-test.ogg") -> "game/audio/resolver-test.ogg",
    // which matches this file's location exactly.
    ClasspathAssetManager manager = new ClasspathAssetManager();
    assertTrue(manager.exists(AssetType.AUDIO, "resolver-test.ogg"));
  }

  @Test
  void existsReturnsFalseForANonExistentResource() {
    ClasspathAssetManager manager = new ClasspathAssetManager();
    assertFalse(manager.exists(AssetType.IMAGE, "does/not/exist/anywhere.png"));
  }
}
