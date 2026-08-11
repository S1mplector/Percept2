package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;

class VnTimelineDiagnosticsTest {

  @Test
  void classifiesInactiveLayersInLightningCompositionAsDeferredWarnings() {
    Fixture fixture = fixture("head_only");
    TimelineData data = parse("""
        timeline {
          parallel {
            mirror "john_body_default" { mirrorX: 1 dur: 1 }
            mirror "john_neck_normal" { mirrorX: 1 dur: 1 }
            mirror "john_arm_front_default" { mirrorX: 1 dur: 1 }
          }
        }
        """);

    VnTimelineDiagnostics.Report report = VnTimelineDiagnostics.diagnose(
        data, fixture.scenario(), fixture.state());

    assertFalse(report.blocksPlayback());
    assertTrue(hasCode(report, VnTimelineDiagnostics.Code.DEFERRED_LAYER_TARGET, "john_body_default"));
    assertTrue(hasCode(report, VnTimelineDiagnostics.Code.DEFERRED_LAYER_TARGET, "john_neck_normal"));
    assertTrue(hasCode(report, VnTimelineDiagnostics.Code.DEFERRED_LAYER_TARGET, "john_arm_front_default"));
    assertTrue(report.warningCount() >= 1, "The 1ms duration should also produce a timing warning");
  }

  @Test
  void rejectsHiddenCharactersUnknownTargetsAndStaleAliases() {
    Fixture hidden = fixture("");
    VnTimelineDiagnostics.Report hiddenReport = diagnose(hidden, "john");
    assertTrue(hasCode(hiddenReport, VnTimelineDiagnostics.Code.CHARACTER_NOT_VISIBLE, "john"));

    Fixture visible = fixture("full_default");
    VnTimelineDiagnostics.Report unknownReport = diagnose(visible, "john_bdy_default");
    assertTrue(hasCode(unknownReport, VnTimelineDiagnostics.Code.UNKNOWN_CHARACTER_TARGET, "john_bdy_default"));

    VnTimelineDiagnostics.Report staleReport = diagnose(visible, "john_old_body_default");
    assertTrue(hasCode(staleReport, VnTimelineDiagnostics.Code.STALE_EXPRESSION_ALIAS, "john_old_body_default"));
    assertFalse(staleReport.blocksPlayback());
    assertTrue(staleReport.findings().stream().anyMatch(finding ->
        finding.code() == VnTimelineDiagnostics.Code.STALE_EXPRESSION_ALIAS
            && finding.quickFix().contains("john_body_default")));
  }

  @Test
  void stableGroupsFollowVariantsWhileExactLayersExplainTheirScope() {
    Fixture defaultFixture = fixture("full_default");
    VnTimelineDiagnostics.Report exactLayer = diagnose(defaultFixture, "john_body_default");
    assertFalse(exactLayer.blocksPlayback());
    assertTrue(hasCode(
        exactLayer,
        VnTimelineDiagnostics.Code.EXACT_LAYER_REPLACEMENT_SCOPE,
        "john_body_default"));
    assertTrue(hasCode(
        exactLayer,
        VnTimelineDiagnostics.Code.PERSISTENT_LAYER_STATE,
        "john_body_default"));

    Fixture holdingFixture = fixture("holding");
    VnTimelineDiagnostics.Report group = diagnose(holdingFixture, "john_body_orientation");
    assertFalse(group.blocksPlayback());
    assertFalse(hasCode(group, VnTimelineDiagnostics.Code.DEFERRED_GROUP_TARGET, "john_body_orientation"));

    VnTimelineDiagnostics.Report replacedExactLayer = diagnose(holdingFixture, "john_body_default");
    assertFalse(replacedExactLayer.blocksPlayback());
    assertTrue(hasCode(replacedExactLayer, VnTimelineDiagnostics.Code.DEFERRED_LAYER_TARGET, "john_body_default"));
  }

  @Test
  void promotesStructuralTimelineErrorsToPlaybackBlockers() {
    Fixture fixture = fixture("full_default");
    TimelineData invalid = new TimelineData("invalid", Double.NaN);

    VnTimelineDiagnostics.Report report = VnTimelineDiagnostics.diagnose(
        invalid, fixture.scenario(), fixture.state());

    assertTrue(report.blocksPlayback());
    assertTrue(hasCode(report, VnTimelineDiagnostics.Code.TIMELINE_DATA, "(timeline)"));
  }

  private static VnTimelineDiagnostics.Report diagnose(Fixture fixture, String target) {
    return VnTimelineDiagnostics.diagnose(
        parse("""
            timeline {
              move "%s" {
                x: 10
                dur: 100
              }
            }
            """.formatted(target)),
        fixture.scenario(),
        fixture.state());
  }

  private static TimelineData parse(String source) {
    return TimelineDataParser.parse("diagnostic_test", source);
  }

  private static boolean hasCode(
      VnTimelineDiagnostics.Report report,
      VnTimelineDiagnostics.Code code,
      String target
  ) {
    return report.findings().stream().anyMatch(finding ->
        finding.code() == code && finding.target().equals(target));
  }

  private static Fixture fixture(String expression) {
    VnCharacter john = VnCharacter.builder("john")
        .addLayer("body_default", "body.png")
        .addLayer("body_no_limbs", "body_no_limbs.png")
        .addLayer("neck_normal", "neck.png")
        .addLayer("arm_front_default", "arm.png")
        .addLayer("arm_front_holding_wrist", "holding.png")
        .addLayer("eyes_n_base", "eyes.png")
        .addExpression("head_only", "head.png", List.of("eyes_n_base"))
        .addExpression("full_default", "full.png", List.of(
            "body_default", "neck_normal", "arm_front_default", "eyes_n_base"))
        .addExpression("holding", "holding_full.png", List.of(
            "body_no_limbs", "neck_normal", "arm_front_holding_wrist", "eyes_n_base"))
        .addLayerGroup("body_orientation", "", List.of("body_default", "body_no_limbs"))
        .addLayerGroup("front_arm", "", List.of("arm_front_default", "arm_front_holding_wrist"))
        .build();
    VnScenario scenario = new VnScenarioBuilder("timeline_diagnostics")
        .addCharacter(john)
        .label("start")
        .end()
        .build();
    VnScene scene = new VnScene(scenario);
    if (expression != null && !expression.isBlank()) {
      scene.getState().showCharacter(CharacterPosition.CENTER, "john", expression);
    }
    return new Fixture(scenario, scene.getState());
  }

  private record Fixture(VnScenario scenario, VnState state) {
  }
}
