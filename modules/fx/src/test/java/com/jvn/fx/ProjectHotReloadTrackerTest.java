package com.jvn.fx;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectHotReloadTrackerTest {

  @TempDir
  Path projectRoot;

  @Test
  void detectsManifestChangesAcrossSharedCategories() throws Exception {
    ProjectHotReloadTracker tracker = ProjectHotReloadTracker.create(projectRoot.toFile());
    assertNotNull(tracker);
    try {
      Files.writeString(projectRoot.resolve("jvn.project"), "runtime.locale=en\n");

      ProjectHotReloadTracker.ChangeSet changes = awaitChanges(
          tracker,
          change -> change.scriptsChanged() && change.uiChanged() && change.localizationChanged()
      );

      assertTrue(changes.scriptsChanged());
      assertTrue(changes.uiChanged());
      assertTrue(changes.localizationChanged());
    } finally {
      tracker.close();
    }
  }

  @Test
  void detectsPhoneConfigCreatedInsideNewDirectories() throws Exception {
    ProjectHotReloadTracker tracker = ProjectHotReloadTracker.create(projectRoot.toFile());
    assertNotNull(tracker);
    try {
      Path phoneConfig = projectRoot.resolve("config/phone/phone.properties");
      Files.createDirectories(phoneConfig.getParent());
      Files.writeString(phoneConfig, "accent=#ffffff\n");

      ProjectHotReloadTracker.ChangeSet changes = awaitChanges(
          tracker,
          ProjectHotReloadTracker.ChangeSet::phoneChanged
      );

      assertTrue(changes.phoneChanged());
    } finally {
      tracker.close();
    }
  }

  private static ProjectHotReloadTracker.ChangeSet awaitChanges(
      ProjectHotReloadTracker tracker,
      Predicate<ProjectHotReloadTracker.ChangeSet> predicate
  ) throws Exception {
    long nowNs = 1_000_000_000L;
    for (int attempt = 0; attempt < 80; attempt++) {
      ProjectHotReloadTracker.ChangeSet changes = tracker.poll(nowNs);
      if (predicate.test(changes)) {
        return changes;
      }
      Thread.sleep(50L);
      nowNs += 500_000_000L;
    }
    fail("Timed out waiting for hot reload change notification");
    return null;
  }
}
