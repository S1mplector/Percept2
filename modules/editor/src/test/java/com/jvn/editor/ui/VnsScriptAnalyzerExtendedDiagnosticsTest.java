package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnsScriptAnalyzerExtendedDiagnosticsTest {

  @TempDir
  Path projectRoot;

  @Test
  void reportsMissingCharacterAndAudioAssets() {
    String source = """
        @scenario asset_check
        @character hero "Hero"
        @charimg hero neutral assets/characters/missing_hero.png
        @charlayer hero eyes assets/characters/missing_eyes.png
        @label start
        [bgm "assets/audio/missing_theme.ogg"]
        [sfx "assets/audio/missing_click.wav"]
        [voice path="assets/audio/missing_voice.ogg"]
        Hero: Ready.
        [end]
        """;

    VnsScriptAnalyzer.Analysis analysis =
        VnsScriptAnalyzer.analyze(source, projectRoot.toFile());

    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_character_asset".equals(d.kind())
            && d.message().contains("missing_hero.png")));
    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_character_asset".equals(d.kind())
            && d.message().contains("missing_eyes.png")));
    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_audio_asset".equals(d.kind())
            && d.message().contains("missing_theme.ogg")));
    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_audio_asset".equals(d.kind())
            && d.message().contains("missing_click.wav")));
    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_audio_asset".equals(d.kind())
            && d.message().contains("missing_voice.ogg")));
  }

  @Test
  void acceptsCharacterAndAudioAssetsThatExist() throws Exception {
    Files.createDirectories(projectRoot.resolve("assets/characters"));
    Files.createDirectories(projectRoot.resolve("assets/audio"));
    Files.writeString(projectRoot.resolve("assets/characters/hero.png"), "image");
    Files.writeString(projectRoot.resolve("assets/audio/theme.ogg"), "audio");

    String source = """
        @scenario valid_assets
        @character hero "Hero"
        @charimg hero neutral assets/characters/hero.png
        @label start
        [bgm assets/audio/theme.ogg]
        Hero: Ready.
        [end]
        """;

    VnsScriptAnalyzer.Analysis analysis =
        VnsScriptAnalyzer.analyze(source, projectRoot.toFile());

    assertFalse(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_character_asset".equals(d.kind())
            || "missing_audio_asset".equals(d.kind())));
  }

  @Test
  void reportsMissingLabelInsideExistingCrossScriptTarget() throws Exception {
    Path story = Files.createDirectories(projectRoot.resolve("scripts/story"));
    Files.writeString(story.resolve("target.vns"), """
        @scenario target
        @label start
        [end]
        """);

    String source = """
        @scenario source
        @label start
        [goto story/target.vns:missing_ending]
        """;

    VnsScriptAnalyzer.Analysis analysis =
        VnsScriptAnalyzer.analyze(source, projectRoot.toFile());

    assertTrue(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_script_label".equals(d.kind())
            && d.message().contains("missing_ending")));
    assertFalse(analysis.diagnostics().stream()
        .anyMatch(d -> "missing_script".equals(d.kind())));
  }

  @Test
  void reportsOnlyFirstDeadStatementAfterTerminalCommand() {
    String source = """
        @scenario dead_code
        @label start
        [jump ending]
        narrator: This cannot run.
        [sfx assets/audio/also_unreachable.wav]

        @label ending
        narrator: This can run.
        [end]
        """;

    VnsScriptAnalyzer.Analysis analysis =
        VnsScriptAnalyzer.analyze(source, null);

    long deadStatements = analysis.diagnostics().stream()
        .filter(d -> "unreachable_statement".equals(d.kind()))
        .count();
    assertTrue(deadStatements == 1);
  }
}
