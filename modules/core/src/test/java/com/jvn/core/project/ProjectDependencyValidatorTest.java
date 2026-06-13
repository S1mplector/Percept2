package com.jvn.core.project;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProjectDependencyValidatorTest {

  @Test
  void reportsMissingUnusedAndBrokenRuntimeReferences() throws Exception {
    Path root = Files.createTempDirectory("jvn-dependency-scan-");
    write(root.resolve("jvn.project"), """
        name=Dependency Scan Demo
        version=1.0.0
        type=vn
        entryVns=story/prologue.vns
        """);
    write(root.resolve("config/locales/en.properties"), "hello=Hello\n");
    write(root.resolve("config/menu/menu.registry"), "menus=main\n");
    write(root.resolve("config/menu/menus/main.menu"), """
        layout=default
        defaultItemStyle=default
        items=gallery
        item.gallery.label=Gallery
        item.gallery.action=open_menu
        item.gallery.target=missing_gallery
        """);
    write(root.resolve("scripts/story/prologue.vns"), """
        @background park assets/backgrounds/park.png
        @background missing assets/backgrounds/missing.png
        @charimg hero neutral assets/characters/hero.png
        @charlayer hero base assets/characters/hero_base.png
        @charpreset hero special $base | assets/characters/hero_smile.png
        @stagepreset sunset config/stage/sunset.stagepreset

        [stage missing_stage]
        [bgm "assets/audio/bgm/theme.ogg"]
        [sfx "assets/audio/sfx/click.ogg"]
        [call jes_timeline missing_timeline]
        Hero "Ready."
        """);
    write(root.resolve("scripts/story/config/stage/sunset.stagepreset"), """
        jvn.stagePreset.id=sunset
        background=park
        lights=0
        """);
    write(root.resolve("scripts/timelines/hero_intro.jes"), """
        timeline {
          playAudio "assets/audio/sfx/hit.ogg" {
            channel: sfx
          }
        }
        """);
    write(root.resolve("assets/backgrounds/park.png"), "");
    write(root.resolve("assets/backgrounds/unused.png"), "");
    write(root.resolve("assets/characters/hero.png"), "");
    write(root.resolve("assets/characters/hero_base.png"), "");
    write(root.resolve("assets/characters/hero_smile.png"), "");
    write(root.resolve("assets/audio/bgm/theme.ogg"), "");
    write(root.resolve("assets/audio/sfx/click.ogg"), "");
    write(root.resolve("assets/audio/sfx/hit.ogg"), "");

    ProjectDependencyValidator.Report report = ProjectDependencyValidator.inspect(root);

    assertFalse(report.hasBlockingIssues());
    assertTrue(hasFinding(report, ProjectDependencyValidator.Severity.WARNING, "asset",
        "assets/backgrounds/missing.png"));
    assertTrue(hasFinding(report, ProjectDependencyValidator.Severity.INFO, "asset",
        "assets/backgrounds/unused.png"));
    assertTrue(hasFinding(report, ProjectDependencyValidator.Severity.WARNING, "stage-preset",
        "missing_stage"));
    assertTrue(hasFinding(report, ProjectDependencyValidator.Severity.WARNING, "timeline",
        "missing_timeline"));
    assertTrue(hasFinding(report, ProjectDependencyValidator.Severity.WARNING, "menu",
        "missing_gallery"));
  }

  @Test
  void treatsMissingEntryScriptAsPackagingBlocker() throws Exception {
    Path root = Files.createTempDirectory("jvn-dependency-packaging-");
    write(root.resolve("jvn.project"), """
        name=Broken Demo
        version=1.0.0
        type=vn
        entryVns=story/missing.vns
        """);
    write(root.resolve("config/locales/en.properties"), "hello=Hello\n");

    ProjectDependencyValidator.Report report = ProjectDependencyValidator.inspect(root);

    assertTrue(report.hasBlockingIssues());
    assertTrue(hasFinding(report, ProjectDependencyValidator.Severity.ERROR, "packaging",
        "story/missing.vns"));
  }

  private static boolean hasFinding(
      ProjectDependencyValidator.Report report,
      ProjectDependencyValidator.Severity severity,
      String category,
      String target
  ) {
    return report.findings().stream().anyMatch(f ->
        f.severity() == severity
            && category.equals(f.category())
            && target.equals(f.target()));
  }

  private static void write(Path file, String content) throws Exception {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
