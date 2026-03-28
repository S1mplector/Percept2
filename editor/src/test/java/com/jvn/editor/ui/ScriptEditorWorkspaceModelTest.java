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
    Files.createDirectories(tempRoot.resolve("config/menu/menus"));

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
    Files.writeString(tempRoot.resolve("config/menu/menus/main.menu"), """
        title=Main
        item.start.text=Start
        """);

    ScriptEditorWorkspaceModel.WorkspaceSnapshot snapshot =
        ScriptEditorWorkspaceModel.index(tempRoot.toFile());

    assertTrue(snapshot.hasContentRoot());
    assertEquals(3, snapshot.scripts().size());
    assertEquals(3, snapshot.folderCount());
    assertEquals(3, snapshot.totalLabelCount());

    ScriptEditorWorkspaceModel.ScriptFileEntry prologue = snapshot.scripts().stream()
        .filter(entry -> entry.relativePath().equals("scripts/story/prologue.vns"))
        .findFirst()
        .orElseThrow();
    assertEquals(5, prologue.lineCount());
    assertEquals(2, prologue.labelCount());
    assertEquals(1, prologue.includeCount());
    assertIterableEquals(List.of("start", "branch"), prologue.labelNames());
    assertEquals("scripts/story/prologue.vns", prologue.projectRelativePath());
    assertEquals(FileEditorTab.Kind.VNS, prologue.kind());

    ScriptEditorWorkspaceModel.ScriptFileEntry menu = snapshot.scripts().stream()
        .filter(entry -> entry.relativePath().equals("config/menu/menus/main.menu"))
        .findFirst()
        .orElseThrow();
    assertEquals(FileEditorTab.Kind.MENU_SCREEN, menu.kind());
    assertEquals(0, menu.labelCount());
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
    assertEquals("scripts/story/new_scene.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath(null));
    assertEquals("scripts/story/chapter_01.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath("story/chapter_01"));
    assertEquals("scripts/story/chapter_02.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath("/scripts/story/chapter_02.vns"));
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

  @Test
  void createTextFileSupportsNonScriptProjectFiles() throws Exception {
    File created = ScriptEditorWorkspaceModel.createTextFile(tempRoot.toFile(), "config/menu/menus/settings.menu");
    assertNotNull(created);
    assertTrue(created.isFile());
    assertTrue(created.getPath().replace('\\', '/').endsWith("config/menu/menus/settings.menu"));

    String text = Files.readString(created.toPath());
    assertTrue(text.contains("title=Settings"));
  }
}
