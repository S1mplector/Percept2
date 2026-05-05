package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhoneAssetsToolViewTest {

  @TempDir
  Path tempDir;

  @Test
  void resolveConfigPathPrefersDirectConfig() throws Exception {
    Path direct = tempDir.resolve(PhoneAssetsToolView.CONFIG_PATH);
    Path game = tempDir.resolve(PhoneAssetsToolView.GAME_CONFIG_PATH);
    Files.createDirectories(direct.getParent());
    Files.createDirectories(game.getParent());
    Files.writeString(direct, "app.title=Direct");
    Files.writeString(game, "app.title=Game");

    assertEquals(direct, PhoneAssetsToolView.resolveConfigPath(tempDir));
  }

  @Test
  void resolveConfigPathFallsBackToGameConfig() throws Exception {
    Path game = tempDir.resolve(PhoneAssetsToolView.GAME_CONFIG_PATH);
    Files.createDirectories(game.getParent());
    Files.writeString(game, "app.title=Game");

    assertEquals(game, PhoneAssetsToolView.resolveConfigPath(tempDir));
  }

  @Test
  void chooseImportTargetAddsNumericSuffixWhenNeeded() throws Exception {
    Path existing = tempDir.resolve("assets/phone/contacts/lily.png");
    Files.createDirectories(existing.getParent());
    Files.writeString(existing, "x");

    Path target = PhoneAssetsToolView.chooseImportTarget(tempDir, "assets/phone/contacts", "lily.png");
    assertEquals(tempDir.resolve("assets/phone/contacts/lily_1.png"), target);
  }

  @Test
  void sanitizeIdNormalizesToSnakeCaseLowercase() {
    assertEquals("lily_event", PhoneAssetsToolView.sanitizeId(" Lily Event "));
    assertEquals("thread_01", PhoneAssetsToolView.sanitizeId("thread-01"));
    assertEquals("", PhoneAssetsToolView.sanitizeId("###"));
  }
}
