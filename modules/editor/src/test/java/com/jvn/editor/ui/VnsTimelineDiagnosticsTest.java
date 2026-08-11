package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnsTimelineDiagnosticsTest {

  @Test
  void mainVnsAnalyzerFlagsLightningTargetsAgainstPrecedingShow(@TempDir Path projectRoot) throws Exception {
    Path definitionsDir = Files.createDirectories(projectRoot.resolve("scripts/definitions"));
    Files.writeString(definitionsDir.resolve("characters.vns"), definitions());
    Path storyDir = Files.createDirectories(projectRoot.resolve("scripts/story"));
    Path script = storyDir.resolve("lightning.vns");
    String source = """
        @include /definitions/characters.vns
        @label start
        [show john center $eyes_n_base+$normal_face_common_05+$normal_mouth_common_01 slot=john z=2]
        timeline {
          parallel {
            mirror "john_body_default" { mirrorX: 1 dur: 1 }
            mirror "john_neck_normal" { mirrorX: 1 dur: 1 }
            mirror "john_arm_front_default" { mirrorX: 1 dur: 1 }
          }
        }
        Narrator: 1584
        """;
    Files.writeString(script, source);

    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(
        source, projectRoot.toFile(), script.toFile());
    Set<String> inactiveTargets = analysis.diagnostics().stream()
        .filter(diagnostic -> diagnostic.kind().equals("timeline_deferred_layer_target"))
        .map(diagnostic -> source.substring(diagnostic.start(), diagnostic.end()))
        .collect(Collectors.toSet());

    assertTrue(inactiveTargets.contains("john_body_default"));
    assertTrue(inactiveTargets.contains("john_neck_normal"));
    assertTrue(inactiveTargets.contains("john_arm_front_default"));
    assertTrue(analysis.diagnostics().stream()
        .filter(diagnostic -> diagnostic.kind().equals("timeline_deferred_layer_target"))
        .allMatch(VnsScriptAnalyzer.Diagnostic::warning));
    assertTrue(analysis.diagnostics().stream().anyMatch(diagnostic ->
        diagnostic.kind().equals("timeline_timeline_data")
            && diagnostic.warning()
            && diagnostic.message().contains("within one 60 Hz display frame")));
  }

  @Test
  void mainVnsAnalyzerAcceptsVisibleStableGroupAcrossReplacement(@TempDir Path projectRoot) throws Exception {
    Path definitionsDir = Files.createDirectories(projectRoot.resolve("scripts/definitions"));
    Files.writeString(definitionsDir.resolve("characters.vns"), definitions());
    Path storyDir = Files.createDirectories(projectRoot.resolve("scripts/story"));
    Path script = storyDir.resolve("group.vns");
    String source = """
        @include /definitions/characters.vns
        @label start
        [show john center holding]
        timeline {
          mirror "john_body_orientation" {
            mirrorX: 1
            dur: 200
          }
        }
        Narrator: group target
        """;
    Files.writeString(script, source);

    VnsScriptAnalyzer.Analysis analysis = VnsScriptAnalyzer.analyze(
        source, projectRoot.toFile(), script.toFile());

    assertFalse(analysis.diagnostics().stream().anyMatch(diagnostic ->
        diagnostic.kind().startsWith("timeline_") && !diagnostic.warning()));
  }

  private static String definitions() {
    return """
        @character john "John"
        @charlayer john body_default assets/john/body.png
        @charlayer john body_no_limbs assets/john/body_no_limbs.png
        @charlayer john neck_normal assets/john/neck.png
        @charlayer john arm_front_default assets/john/arm.png
        @charlayer john arm_front_holding_wrist assets/john/holding.png
        @charlayer john eyes_n_base assets/john/eyes.png
        @charlayer john normal_face_common_05 assets/john/face.png
        @charlayer john normal_mouth_common_01 assets/john/mouth.png
        @charpreset john head_only $eyes_n_base | $normal_face_common_05 | $normal_mouth_common_01
        @charpreset john full_default $body_default | $neck_normal | $arm_front_default | $eyes_n_base | $normal_face_common_05 | $normal_mouth_common_01
        @charpreset john holding $body_no_limbs | $neck_normal | $arm_front_holding_wrist | $eyes_n_base | $normal_face_common_05 | $normal_mouth_common_01
        @chargroup john body_orientation $body_default | $body_no_limbs
        """;
  }
}
