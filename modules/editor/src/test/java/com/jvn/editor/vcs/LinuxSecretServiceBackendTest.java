package com.jvn.editor.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinuxSecretServiceBackendTest {

  private LinuxSecretServiceBackend backend;

  @BeforeEach
  void setUp() {
    String osName = System.getProperty("os.name", "").toLowerCase();
    Assumptions.assumeTrue(osName.contains("nux") || osName.contains("nix"),
        "Secret Service only available on Linux");
    backend = new LinuxSecretServiceBackend();
    Assumptions.assumeTrue(backend.isAvailable(),
        "No Secret Service / D-Bus session available in this environment");
    try {
      backend.clear();
    } catch (Exception ignored) {
      // no pre-existing entry
    }
  }

  @AfterEach
  void tearDown() throws Exception {
    if (backend != null && backend.isAvailable()) backend.clear();
  }

  @Test
  void hasNoTokenBeforeAnythingIsSaved() {
    assertFalse(backend.exists());
  }

  @Test
  void loadTokenReturnsEmptyWhenNothingIsSaved() throws Exception {
    assertEquals(Optional.empty(), backend.load());
  }

  @Test
  void savedTokenRoundTripsExactly() throws Exception {
    backend.save("ghp_exampleToken123");

    assertTrue(backend.exists());
    assertEquals(Optional.of("ghp_exampleToken123"), backend.load());
  }

  @Test
  void clearRemovesTokenAndLoadReturnsEmpty() throws Exception {
    backend.save("ghp_exampleToken123");
    assertTrue(backend.exists());

    backend.clear();

    assertFalse(backend.exists());
    assertEquals(Optional.empty(), backend.load());
  }

  @Test
  void savingBlankTokenThrows() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> backend.save(" "));
  }
}
