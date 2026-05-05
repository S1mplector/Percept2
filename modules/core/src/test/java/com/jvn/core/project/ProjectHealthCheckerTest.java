package com.jvn.core.project;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProjectHealthCheckerTest {

  @Test
  void aggregatesMenuDialoguePhoneAndLocalizationDiagnostics() throws Exception {
    Path root = Files.createTempDirectory("jvn-project-health-");
    Files.createDirectories(root.resolve("scripts/story"));
    Files.createDirectories(root.resolve("config/menu/menus"));
    Files.createDirectories(root.resolve("config/ui"));
    Files.createDirectories(root.resolve("config/phone"));

    Files.writeString(root.resolve("jvn.project"), """
        entryVns=story/prologue.vns
        runtime.locale=ja
        """);
    Files.writeString(root.resolve("scripts/story/prologue.vns"), """
        @label start
        Narrator "Hello."
        """);
    Files.writeString(root.resolve("config/menu/menu.registry"), "menus=settings\n");
    Files.writeString(root.resolve("config/menu/menus/settings.menu"), """
        layout=settings
        defaultItemStyle=settings
        backgroundAsset=assets/ui/missing_menu_bg.png
        items=audio_tab
        item.audio_tab.label=Audio
        item.audio_tab.action=settings_menu
        item.audio_tab.target=settings_audio
        """);
    Files.writeString(root.resolve("config/ui/dialogue.layout"), """
        textBoxAsset=assets/ui/missing_textbox.png
        textBoxButton.ids=help
        textBoxButton.help.action=teleport_menu
        """);
    Files.writeString(root.resolve("config/phone/phone.properties"), """
        app.wallpaper=assets/ui/missing_phone_wallpaper.png
        app.unknownField=surprise
        """);

    ProjectHealthChecker.Report report = ProjectHealthChecker.inspect(root.toFile());

    assertTrue(report.diagnostics().stream().anyMatch(d -> "menu".equals(d.category())));
    assertTrue(report.diagnostics().stream().anyMatch(d -> "dialogue".equals(d.category())));
    assertTrue(report.diagnostics().stream().anyMatch(d -> "phone".equals(d.category())));
    assertTrue(report.diagnostics().stream().anyMatch(d -> "localization".equals(d.category())));
  }
}
