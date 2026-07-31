package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFileIconsTest {
  @TempDir Path tempDir;

  @Test
  void kindForMatchesNewProjectWizardFileTypes() {
    assertEquals(ProjectFileIcons.Kind.ROOT, ProjectFileIcons.kindFor("game", true, true));
    assertEquals(ProjectFileIcons.Kind.SCRIPT_FOLDER, ProjectFileIcons.kindFor("scripts", true, false));
    assertEquals(ProjectFileIcons.Kind.STORY, ProjectFileIcons.kindFor("prologue.vns", false, false));
    assertEquals(ProjectFileIcons.Kind.MENU, ProjectFileIcons.kindFor("main.menu", false, false));
    assertEquals(ProjectFileIcons.Kind.MENU, ProjectFileIcons.kindFor("menu.registry", false, false));
    assertEquals(ProjectFileIcons.Kind.LAYOUT, ProjectFileIcons.kindFor("dialogue.layout", false, false));
    assertEquals(ProjectFileIcons.Kind.STYLE, ProjectFileIcons.kindFor("default.style", false, false));
    assertEquals(ProjectFileIcons.Kind.STYLE, ProjectFileIcons.kindFor("menu.theme", false, false));
    assertEquals(ProjectFileIcons.Kind.TIMELINE, ProjectFileIcons.kindFor("story.storymap", false, false));
    assertEquals(ProjectFileIcons.Kind.TIMELINE, ProjectFileIcons.kindFor("story.timeline", false, false));
    assertEquals(ProjectFileIcons.Kind.SCRIPT, ProjectFileIcons.kindFor("intro.jes", false, false));
    assertEquals(ProjectFileIcons.Kind.MARKDOWN, ProjectFileIcons.kindFor("README.md", false, false));
  }

  @Test
  void projectKindsUseStandardFreedesktopIconNames() {
    assertEquals(List.of("folder-pictures", "folder"),
        ProjectFileIcons.systemIconNames(ProjectFileIcons.Kind.ASSET_FOLDER));
    assertEquals("text-x-java-source",
        ProjectFileIcons.systemIconNames(ProjectFileIcons.Kind.JAVA).getFirst());
    assertEquals("image-x-generic",
        ProjectFileIcons.systemIconNames(ProjectFileIcons.Kind.IMAGE).getFirst());
  }

  @Test
  void activeThemeLookupUsesClosestPngAndFollowsInheritance() throws Exception {
    Path icons = tempDir.resolve("icons");
    Path classic = Files.createDirectories(icons.resolve("Classic"));
    Files.writeString(classic.resolve("index.theme"), "[Icon Theme]\nInherits=Fallback\n");
    Path document = Files.createDirectories(classic.resolve("22x22/mimetypes"))
        .resolve("text-x-generic.png");
    Files.write(document, new byte[]{1});

    Path fallback = Files.createDirectories(icons.resolve("Fallback"));
    Files.writeString(fallback.resolve("index.theme"), "[Icon Theme]\n");
    Path folder = Files.createDirectories(fallback.resolve("16x16/places"))
        .resolve("folder.png");
    Files.write(folder, new byte[]{1});

    assertEquals(document, FreedesktopProjectIconPack.resolveIconPath(
        List.of(icons), "Classic", List.of("text-x-generic"), 18).orElseThrow());
    assertEquals(folder, FreedesktopProjectIconPack.resolveIconPath(
        List.of(icons), "Classic", List.of("folder"), 18).orElseThrow());
  }

  @Test
  void gtkThemeSettingAcceptsQuotedAndPlainValues() {
    assertEquals("Mint-X-Grey", FreedesktopProjectIconPack.parseThemeSetting(
        "[Settings]\ngtk-icon-theme-name='Mint-X-Grey'\n"));
    assertEquals("Adwaita", FreedesktopProjectIconPack.parseThemeSetting(
        "gtk-icon-theme-name=Adwaita\n"));
    assertTrue(!FreedesktopProjectIconPack.activeThemeName().isBlank());
  }
}
