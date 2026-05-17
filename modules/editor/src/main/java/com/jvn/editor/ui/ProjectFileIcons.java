package com.jvn.editor.ui;

import java.io.File;
import java.util.Locale;

import javafx.scene.layout.Region;

final class ProjectFileIcons {
  enum Kind {
    ROOT,
    FOLDER,
    SCRIPT,
    MENU,
    LAYOUT,
    STYLE,
    TIMELINE,
    DOCUMENT,
    NOTE
  }

  private ProjectFileIcons() {}

  static Kind kindFor(File file, File rootDir) {
    if (file == null) return Kind.DOCUMENT;
    boolean root = rootDir != null && file.getAbsoluteFile().equals(rootDir.getAbsoluteFile());
    return kindFor(file.getName(), file.isDirectory(), root);
  }

  static Kind kindFor(String name, boolean directory, boolean root) {
    if (root) return Kind.ROOT;
    if (name == null || name.isBlank()) return Kind.DOCUMENT;
    if (name.startsWith("(")) return Kind.NOTE;
    if (directory) return Kind.FOLDER;

    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".vns")) return Kind.SCRIPT;
    if (lower.endsWith(".menu") || lower.endsWith(".registry")) return Kind.MENU;
    if (lower.endsWith(".layout")) return Kind.LAYOUT;
    if (lower.endsWith(".style") || lower.endsWith(".theme")) return Kind.STYLE;
    if (lower.endsWith(".storymap") || lower.endsWith(".timeline") || lower.endsWith(".jes")) return Kind.TIMELINE;
    return Kind.DOCUMENT;
  }

  static Region iconFor(Kind kind) {
    return iconFor(kind, 14);
  }

  static Region iconFor(Kind kind, double size) {
    return switch (kind != null ? kind : Kind.DOCUMENT) {
      case ROOT -> CssIcon.folder("#d5b36a", size);
      case FOLDER -> CssIcon.folder("#cbb27b", size);
      case SCRIPT -> CssIcon.speech("#8bcf98");
      case MENU -> CssIcon.list("#dccba2");
      case LAYOUT -> CssIcon.grid("#8ec7dd");
      case STYLE -> CssIcon.palette("#d6b4ff");
      case TIMELINE -> CssIcon.play("#dd9a48");
      case NOTE -> CssIcon.warning("#efbf82");
      case DOCUMENT -> CssIcon.document("#c6d1dc");
    };
  }
}
