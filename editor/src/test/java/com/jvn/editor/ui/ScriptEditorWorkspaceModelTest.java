package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptEditorWorkspaceModelTest {

  @TempDir
  Path tempRoot;

  @Test
  void indexCollectsScriptMetadataAndWorkspaceStats() throws Exception {
    Path scripts = Files.createDirectories(tempRoot.resolve("scripts"));
    Files.createDirectories(scripts.resolve("story"));
    Files.createDirectories(scripts.resolve("defs"));

    Files.writeString(scripts.resolve("story/prologue.vns"), """
        @include /defs/characters.vns
        @label start
        narrator: Hello
        @label branch
        narrator: Bye
        """);
    Files.writeString(scripts.resolve("defs/characters.vns"), """
        @label catalog
        narrator: Definitions
        """);

    ScriptEditorWorkspaceModel.WorkspaceSnapshot snapshot =
        ScriptEditorWorkspaceModel.index(tempRoot.toFile());

    assertTrue(snapshot.hasScriptsRoot());
    assertEquals(2, snapshot.scripts().size());
    assertEquals(2, snapshot.folderCount());
    assertEquals(3, snapshot.totalLabelCount());

    ScriptEditorWorkspaceModel.ScriptFileEntry prologue = snapshot.scripts().stream()
        .filter(entry -> entry.relativePath().equals("story/prologue.vns"))
        .findFirst()
        .orElseThrow();
    assertEquals(5, prologue.lineCount());
    assertEquals(2, prologue.labelCount());
    assertEquals(1, prologue.includeCount());
    assertIterableEquals(List.of("start", "branch"), prologue.labelNames());
    assertEquals("scripts/story/prologue.vns", prologue.projectRelativePath());
  }

  @Test
  void filterMatchesRelativePathsAndLabels() throws Exception {
    Path scripts = Files.createDirectories(tempRoot.resolve("scripts/story"));
    Files.writeString(scripts.resolve("chapter_one.vns"), """
        @label intro
        narrator: Start
        """);
    Files.writeString(scripts.resolve("chapter_two.vns"), """
        @label epilogue
        narrator: End
        """);

    List<ScriptEditorWorkspaceModel.ScriptFileEntry> entries =
        ScriptEditorWorkspaceModel.index(tempRoot.toFile()).scripts();

    assertEquals(
        List.of("chapter_one.vns"),
        ScriptEditorWorkspaceModel.filter(entries, "intro").stream()
            .map(ScriptEditorWorkspaceModel.ScriptFileEntry::displayName)
            .toList());
    assertEquals(
        List.of("chapter_two.vns"),
        ScriptEditorWorkspaceModel.filter(entries, "chapter_two").stream()
            .map(ScriptEditorWorkspaceModel.ScriptFileEntry::displayName)
            .toList());
  }

  @Test
  void normalizeRelativeScriptPathKeepsPathsInsideScriptsTree() {
    assertEquals("story/new_scene.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath(null));
    assertEquals("story/chapter_01.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath("story/chapter_01"));
    assertEquals("story/chapter_02.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath("/scripts/story/chapter_02.vns"));
  }

  @Test
  void createScriptBuildsDirectoriesAndWritesDefaultTemplate() throws Exception {
    File created = ScriptEditorWorkspaceModel.createScript(tempRoot.toFile(), "story/act1/opening");
    assertNotNull(created);
    assertTrue(created.isFile());
    assertTrue(created.getPath().replace('\\', '/').endsWith("scripts/story/act1/opening.vns"));

    String text = Files.readString(created.toPath());
    assertTrue(text.contains("@label start"));
    assertTrue(text.contains("narrator: TODO"));

    File secondCall = ScriptEditorWorkspaceModel.createScript(tempRoot.toFile(), "story/act1/opening.vns");
    assertEquals(created.getAbsolutePath(), secondCall.getAbsolutePath());
    assertFalse(Files.readString(secondCall.toPath()).isBlank());
  }
}
