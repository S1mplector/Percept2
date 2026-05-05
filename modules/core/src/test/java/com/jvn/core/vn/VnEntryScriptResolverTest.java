package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnEntryScriptResolverTest {

  @TempDir
  Path tempProjectRoot;

  @Test
  void resolveEntryScriptPrefersExplicitScript() throws Exception {
    Path project = Files.createDirectories(tempProjectRoot.resolve("project-explicit"));
    Files.writeString(project.resolve("jvn.project"), "entryVns=scripts/story/from_manifest.vns\n");

    String previous = System.getProperty("jvn.entryVns");
    VnEntryScriptResolver.publishToSystemProperty("scripts/story/from_property.vns");
    try {
      String resolved = VnEntryScriptResolver.resolveEntryScript("scripts/story/from_explicit.vns", project.toFile());
      assertEquals("story/from_explicit.vns", resolved);
    } finally {
      restoreEntryProperty(previous);
    }
  }

  @Test
  void resolveEntryScriptUsesManifestWhenExplicitMissing() throws Exception {
    Path project = Files.createDirectories(tempProjectRoot.resolve("project-manifest"));
    Files.writeString(project.resolve("jvn.project"), "entryVns=scripts/story/from_manifest.vns\n");

    String previous = System.getProperty("jvn.entryVns");
    VnEntryScriptResolver.publishToSystemProperty("scripts/story/from_property.vns");
    try {
      String resolved = VnEntryScriptResolver.resolveEntryScript(null, project.toFile());
      assertEquals("story/from_manifest.vns", resolved);
    } finally {
      restoreEntryProperty(previous);
    }
  }

  @Test
  void resolveEntryScriptUsesSystemPropertyWhenManifestMissing() throws Exception {
    Path project = Files.createDirectories(tempProjectRoot.resolve("project-property"));

    String previous = System.getProperty("jvn.entryVns");
    VnEntryScriptResolver.publishToSystemProperty("scripts/story/from_property.vns");
    try {
      String resolved = VnEntryScriptResolver.resolveEntryScript(null, project.toFile());
      assertEquals("story/from_property.vns", resolved);
    } finally {
      restoreEntryProperty(previous);
    }
  }

  @Test
  void resolveEntryScriptDiscoversProjectScriptWhenNoConfigProvided() throws Exception {
    Path project = Files.createDirectories(tempProjectRoot.resolve("project-discovery"));
    Path scripts = Files.createDirectories(project.resolve("scripts/story"));
    Files.writeString(scripts.resolve("chapter_01.vns"), "# chapter");
    Files.writeString(scripts.resolve("prologue.vns"), "# prologue");

    String previous = System.getProperty("jvn.entryVns");
    VnEntryScriptResolver.publishToSystemProperty(null);
    try {
      String resolved = VnEntryScriptResolver.resolveEntryScript(null, project.toFile());
      assertNotNull(resolved);
      assertEquals("story/prologue.vns", resolved);
    } finally {
      restoreEntryProperty(previous);
    }
  }

  private static void restoreEntryProperty(String previous) {
    if (previous == null || previous.isBlank()) {
      VnEntryScriptResolver.publishToSystemProperty(null);
    } else {
      VnEntryScriptResolver.publishToSystemProperty(previous);
    }
  }
}
