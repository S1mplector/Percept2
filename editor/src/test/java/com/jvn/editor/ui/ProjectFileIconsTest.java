package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProjectFileIconsTest {

  @Test
  void kindForMatchesNewProjectWizardFileTypes() {
    assertEquals(ProjectFileIcons.Kind.ROOT, ProjectFileIcons.kindFor("game", true, true));
    assertEquals(ProjectFileIcons.Kind.FOLDER, ProjectFileIcons.kindFor("scripts", true, false));
    assertEquals(ProjectFileIcons.Kind.SCRIPT, ProjectFileIcons.kindFor("prologue.vns", false, false));
    assertEquals(ProjectFileIcons.Kind.MENU, ProjectFileIcons.kindFor("main.menu", false, false));
    assertEquals(ProjectFileIcons.Kind.MENU, ProjectFileIcons.kindFor("menu.registry", false, false));
    assertEquals(ProjectFileIcons.Kind.LAYOUT, ProjectFileIcons.kindFor("dialogue.layout", false, false));
    assertEquals(ProjectFileIcons.Kind.STYLE, ProjectFileIcons.kindFor("default.style", false, false));
    assertEquals(ProjectFileIcons.Kind.STYLE, ProjectFileIcons.kindFor("menu.theme", false, false));
    assertEquals(ProjectFileIcons.Kind.TIMELINE, ProjectFileIcons.kindFor("story.timeline", false, false));
    assertEquals(ProjectFileIcons.Kind.TIMELINE, ProjectFileIcons.kindFor("intro.jes", false, false));
    assertEquals(ProjectFileIcons.Kind.DOCUMENT, ProjectFileIcons.kindFor("README.md", false, false));
  }
}
