package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WhatsNewCatalogTest {

  @Test
  void requestsPopupOnlyWhenVersionChanges() {
    assertTrue(WhatsNewCatalog.shouldShow("v0.4.3.1", ""));
    assertTrue(WhatsNewCatalog.shouldShow("v0.4.3.1", "v0.4.3"));
    assertTrue(WhatsNewCatalog.shouldShow("v0.4.3.1", "v0.4.3.1 Beta"));
    assertFalse(WhatsNewCatalog.shouldShow("v0.4.3.1", " v0.4.3.1 "));
    assertFalse(WhatsNewCatalog.shouldShow("", "v0.4.1"));
  }

  @Test
  void currentReleaseContainsCuratedDetailedNotes() {
    WhatsNewCatalog.Release release = WhatsNewCatalog.forVersion("v0.4.3.1");

    assertTrue(release.curated());
    assertEquals("v0.4.3.1", release.versionLabel());
    assertTrue(release.summary().contains("included files"));
  }

  @Test
  void maturityBuildUsesNumericReleaseNotesButKeepsFullLabel() {
    WhatsNewCatalog.Release release = WhatsNewCatalog.forVersion("v0.4.3.1 Beta");

    assertTrue(release.curated());
    assertEquals("v0.4.3.1 Beta", release.versionLabel());
  }

  @Test
  void unknownVersionStillGetsGracefulVersionSpecificScreen() {
    WhatsNewCatalog.Release release = WhatsNewCatalog.forVersion("v9.1.0");

    assertFalse(release.curated());
    assertEquals("v9.1.0", release.versionLabel());
    assertFalse(release.sections().isEmpty());
  }
}
