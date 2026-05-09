package com.jvn.core.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class StoryMapPathsTest {
  @Test
  void prefersStoryMapManifestKeyOverLegacyTimelineKey() throws Exception {
    Path root = Files.createTempDirectory("jvn-story-map-");
    Properties manifest = new Properties();
    manifest.setProperty("storyMap", "config/story/custom.storymap");
    manifest.setProperty("timeline", "config/timeline/story.timeline");

    assertEquals(
        root.resolve("config/story/custom.storymap").toFile(),
        StoryMapPaths.resolveExistingOrDefault(root.toFile(), manifest)
    );
  }

  @Test
  void fallsBackToExistingLegacyTimelineFile() throws Exception {
    Path root = Files.createTempDirectory("jvn-story-map-legacy-");
    Files.createDirectories(root.resolve("config/timeline"));
    Files.writeString(root.resolve("config/timeline/story.timeline"), "# legacy\n");

    assertEquals(
        root.resolve("config/timeline/story.timeline").toFile(),
        StoryMapPaths.resolveExistingOrDefault(root.toFile(), null)
    );
  }

  @Test
  void usesNewDefaultWhenNoStoryMapExists() throws Exception {
    Path root = Files.createTempDirectory("jvn-story-map-default-");

    assertEquals(
        root.resolve("config/story/story.storymap").toFile(),
        StoryMapPaths.resolveExistingOrDefault(root.toFile(), null)
    );
  }

  @Test
  void resolveForProjectRootReadsManifestStoryMapKey() throws Exception {
    Path root = Files.createTempDirectory("jvn-story-map-manifest-");
    Files.writeString(root.resolve("jvn.project"), "storyMap=story/custom.storymap\n");

    assertEquals(
        root.resolve("story/custom.storymap").toFile(),
        StoryMapPaths.resolveForProjectRoot(root.toFile())
    );
  }
}
