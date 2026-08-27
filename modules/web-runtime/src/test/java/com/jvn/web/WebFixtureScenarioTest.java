package com.jvn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioLoader;
import org.junit.jupiter.api.Test;

class WebFixtureScenarioTest {

  @Test
  void fixtureScenarioLoadsWithBackgroundCharacterDialogueAndChoice() throws Exception {
    VnScenario scenario = new VnScenarioLoader().load("story/web_fixture.vns");

    assertNotNull(scenario);
    assertEquals("web_fixture", scenario.getId());
    assertTrue(scenario.getCharacters().containsKey("lavender"),
        "expected the 'lavender' character to be declared");
    assertNotNull(scenario.getBackground("game"),
        "expected the 'game' background to be declared");
  }
}
