package com.jvn.core.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AudioAssetResolverTest {
  @TempDir
  Path tempDir;

  @Test
  void resolvesProjectAudioViaStandardAndPrefixedPaths() throws Exception {
    Path projectRoot = tempDir.resolve("demo_project");
    Path audio = projectRoot.resolve("assets/audio/bgm/theme.ogg");
    Files.createDirectories(audio.getParent());
    Files.writeString(audio, "placeholder");

    File resolvedBare = AudioAssetResolver.resolveFile(projectRoot.toFile(), "bgm/theme.ogg");
    File resolvedAssetPath = AudioAssetResolver.resolveFile(projectRoot.toFile(), "assets/audio/bgm/theme.ogg");
    File resolvedPrefixed = AudioAssetResolver.resolveFile(projectRoot.toFile(), "demo_project/assets/audio/bgm/theme.ogg");

    assertNotNull(resolvedBare);
    assertEquals(audio.toFile().getCanonicalFile(), resolvedBare.getCanonicalFile());
    assertEquals(audio.toFile().getCanonicalFile(), resolvedAssetPath.getCanonicalFile());
    assertEquals(audio.toFile().getCanonicalFile(), resolvedPrefixed.getCanonicalFile());
  }

  @Test
  void normalizesWindowsStyleSeparatorsForProjectAudio() throws Exception {
    Path projectRoot = tempDir.resolve("windows_project");
    Path audio = projectRoot.resolve("game/audio/ui/click.wav");
    Files.createDirectories(audio.getParent());
    Files.writeString(audio, "placeholder");

    File resolved = AudioAssetResolver.resolveFile(projectRoot.toFile(), "windows_project\\game\\audio\\ui\\click.wav");

    assertNotNull(resolved);
    assertEquals(audio.toFile().getCanonicalFile(), resolved.getCanonicalFile());
  }

  @Test
  void resolvesClasspathAudioCandidates() {
    URL url = AudioAssetResolver.resolveClasspathUrl(getClass().getClassLoader(), "resolver-test.ogg");
    assertNotNull(url);
  }
}
