package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.vn.script.VnScriptParser;

class VnScenarioLoaderIncludeTest {

  @TempDir
  Path tempProjectRoot;

  @Test
  void loadSupportsIncludedScriptsWithScriptsRootAbsoluteIncludePath() throws Exception {
    Path storyDir = Files.createDirectories(tempProjectRoot.resolve("scripts/story"));
    Path definitionsDir = Files.createDirectories(tempProjectRoot.resolve("scripts/definitions"));

    Files.writeString(storyDir.resolve("prologue.vns"), """
        @scenario include_demo
        @include /definitions/characters.vns

        @label start
        [show lavender center idle]
        Lavender: Include loaded.
        [end]
        """);

    Files.writeString(definitionsDir.resolve("characters.vns"), """
        @character lavender "Lavender"
        @charimg lavender idle assets/characters/sprites/lavender.png
        """);

    VnScenarioLoader loader = new VnScenarioLoader(
        new AssetCatalog(new FilesystemAssetManager(tempProjectRoot)),
        new VnScriptParser(),
        "game/scripts/");

    VnScenario scenario = loader.load("story/prologue.vns");

    assertEquals("include_demo", scenario.getId());
    assertNotNull(scenario.getCharacter("lavender"));
    assertNotNull(scenario.getLabelIndex("start"));
  }

  @Test
  void loadMapsDeprecatedDemoScriptNameToStoryPrologueWhenPresent() throws Exception {
    Path storyDir = Files.createDirectories(tempProjectRoot.resolve("scripts/story"));
    Files.writeString(storyDir.resolve("prologue.vns"), """
        @scenario migrated_prologue
        @label start
        Narrator: Migrated entry script.
        [end]
        """);

    VnScenarioLoader loader = new VnScenarioLoader(
        new AssetCatalog(new FilesystemAssetManager(tempProjectRoot)),
        new VnScriptParser(),
        "game/scripts/");

    VnScenario scenario = loader.load("demo.vns");

    assertEquals("migrated_prologue", scenario.getId());
    assertNotNull(scenario.getLabelIndex("start"));
  }
}
