package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VnStoragePathsTest {
  private String previousGameId;
  private String previousStorageRoot;

  @BeforeEach
  void rememberProperties() {
    previousGameId = System.getProperty(VnStoragePaths.GAME_ID_PROPERTY);
    previousStorageRoot = System.getProperty(VnStoragePaths.STORAGE_ROOT_PROPERTY);
    System.clearProperty(VnStoragePaths.STORAGE_ROOT_PROPERTY);
  }

  @AfterEach
  void restoreProperties() {
    restore(VnStoragePaths.GAME_ID_PROPERTY, previousGameId);
    restore(VnStoragePaths.STORAGE_ROOT_PROPERTY, previousStorageRoot);
  }

  @Test
  void scopesRuntimeFilesBelowStableGameDirectory() {
    VnStoragePaths.configureGame("Studio / Moon Story");

    Path expectedRoot = Path.of(System.getProperty("user.home"), ".jvn", "games", "studio-moon-story");
    assertEquals(expectedRoot, VnStoragePaths.root());
    assertEquals(expectedRoot.resolve("saves"), VnStoragePaths.saves());
    assertEquals(expectedRoot.resolve("persistent.json"), VnStoragePaths.persistentData());
    assertEquals(expectedRoot.resolve("settings.properties"), VnStoragePaths.settings());
  }

  @Test
  void preservesLegacyRootWhenNoGameIsConfigured() {
    VnStoragePaths.configureGame(null);

    assertEquals(Path.of(System.getProperty("user.home"), ".jvn"), VnStoragePaths.root());
  }

  @Test
  void prefersManifestIdAndSanitizesItForUseAsAPathSegment() {
    Properties manifest = new Properties();
    manifest.setProperty("id", "../../My Game: Deluxe");
    manifest.setProperty("name", "Ignored Name");

    assertEquals("my-game-deluxe", VnStoragePaths.resolveGameId(manifest, Path.of("fallback")));
  }

  @Test
  void derivesIdForOlderManifestsWithoutChangingWithInstallationPath() {
    Properties manifest = new Properties();
    manifest.setProperty("author", "Paper Crane");
    manifest.setProperty("name", "After the Rain");

    assertEquals(
        "paper-crane-after-the-rain",
        VnStoragePaths.resolveGameId(manifest, Path.of("/first/install")));
    assertEquals(
        "paper-crane-after-the-rain",
        VnStoragePaths.resolveGameId(manifest, Path.of("/another/install")));
  }

  @Test
  void sanitizeGameIdHandlesNonAsciiInputWithoutThrowing() {
    // Documents the accepted behavior narrowing from dropping NFKD transliteration:
    // non-ASCII letters (e.g. accented Latin) are no longer transliterated to their
    // base letter before sanitization; they are filtered out by the ASCII-only regex.
    String result = VnStoragePaths.sanitizeGameId("Café Königreich");
    assertEquals("caf-k-nigreich", result);
  }

  @Test
  void explicitStorageRootOverridesGameScoping() {
    System.setProperty(VnStoragePaths.STORAGE_ROOT_PROPERTY, "build/test-storage");
    VnStoragePaths.configureGame("game-one");

    assertEquals(Path.of("build/test-storage").toAbsolutePath().normalize(), VnStoragePaths.root());
  }

  private static void restore(String key, String value) {
    if (value == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, value);
    }
  }
}
