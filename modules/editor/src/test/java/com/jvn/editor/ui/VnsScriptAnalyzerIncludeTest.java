package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnsScriptAnalyzerIncludeTest {

  @TempDir
  Path tempProjectRoot;

  @Test
  void analyzeDoesNotEmitIncludeResolverErrorWhenProjectScriptsAreAvailable() throws Exception {
    Path storyDir = Files.createDirectories(tempProjectRoot.resolve("scripts/story"));
    Path definitionsDir = Files.createDirectories(tempProjectRoot.resolve("scripts/definitions"));
    Path script = storyDir.resolve("prologue.vns");

    Files.writeString(script, """
        @scenario include_demo
        @include /definitions/characters.vns
        @label start
        [show lavender center neutral]
        Lavender: Ready.
        [end]
        """);

    Files.writeString(definitionsDir.resolve("characters.vns"), """
        @character lavender "Lavender"
        @charimg lavender neutral assets/characters/sprites/lavender.png
        """);

    String source = Files.readString(script);
    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(source, tempProjectRoot.toFile(), script.toFile());

    boolean hasParseError = analysis.diagnostics().stream()
        .anyMatch(d -> "parse_error".equalsIgnoreCase(d.kind()));
    assertFalse(hasParseError, "Expected include-aware parse to succeed in diagnostics analysis");
  }

  @Test
  void analyzeRecoversWhenCallerPassesNestedStoryFolderAsProjectRoot() throws Exception {
    Path storyDir = Files.createDirectories(tempProjectRoot.resolve("scripts/story"));
    Path definitionsDir = Files.createDirectories(tempProjectRoot.resolve("scripts/definitions"));
    Path script = storyDir.resolve("prologue.vns");

    Files.writeString(script, """
        @scenario include_demo
        @include /definitions/characters.vns
        @label start
        [show lavender center neutral]
        Lavender: Ready.
        [end]
        """);
    Files.writeString(definitionsDir.resolve("characters.vns"), """
        @character lavender "Lavender"
        @charimg lavender neutral assets/characters/sprites/lavender.png
        """);

    String source = Files.readString(script);
    File misScopedRoot = storyDir.toFile();
    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(source, misScopedRoot, script.toFile());

    boolean hasParseError = analysis.diagnostics().stream()
        .anyMatch(d -> "parse_error".equalsIgnoreCase(d.kind()));
    assertFalse(hasParseError, "Expected analyzer to infer project root from scripts ancestor");
  }

  @Test
  void analyzeReportsMissingBridgeScripts() throws Exception {
    Path storyDir = Files.createDirectories(tempProjectRoot.resolve("scripts/story"));
    Path script = storyDir.resolve("prologue.vns");

    Files.writeString(script, """
        @scenario bridge_scripts
        @label start
        [load story/missing_route.vns]
        [jes_push scripts/missing_scene.jes]
        [end]
        """);

    VnsScriptAnalyzer.Analysis analysis =
        VnsScriptAnalyzer.analyze(Files.readString(script), tempProjectRoot.toFile(), script.toFile());

    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_script".equals(d.kind()) && d.message().contains("story/missing_route.vns")));
    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_script".equals(d.kind()) && d.message().contains("scripts/missing_scene.jes")));
  }
}
