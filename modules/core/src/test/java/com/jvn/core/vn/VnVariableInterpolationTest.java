package com.jvn.core.vn;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VnVariableInterpolationTest {
  @Test
  void interpolatesKnownVariablesAndBlanksMissing() {
    String resolved = VnVariableInterpolator.interpolate(
      "Welcome ${player}! Score=${score}, Rank=${rank}",
      Map.of("player", "Ari", "score", 42)
    );

    assertEquals("Welcome Ari! Score=42, Rank=", resolved);
  }

  @Test
  void interpolationIsSinglePass() {
    String resolved = VnVariableInterpolator.interpolate(
      "${outer}",
      Map.of("outer", "${inner}", "inner", "done")
    );

    assertEquals("${inner}", resolved);
  }

  @Test
  void vnSceneUsesResolvedTextForHistoryAndHud() {
    VnScenario scenario = new VnScenarioBuilder("interp_story")
      .dialogue("Narrator", "Welcome back, ${playerName}!")
      .end()
      .build();
    VnScene scene = new VnScene(scenario);
    scene.getState().setVariable("playerName", "Ilgaz");

    scene.onEnter();

    VnHistory.HistoryEntry entry = scene.getState().getHistory().getEntries().get(0);
    assertEquals("Narrator", entry.getSpeaker());
    assertEquals("Welcome back, Ilgaz!", entry.getText());

    scene.getState().setVariable("score", 9001);
    scene.getState().showHudMessage("Score: ${score} / ${missing}", 1500);
    assertEquals("Score: 9001 / ", scene.getState().getHudMessage());
  }
}
