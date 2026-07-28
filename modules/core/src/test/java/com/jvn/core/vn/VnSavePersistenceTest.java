package com.jvn.core.vn;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;

public class VnSavePersistenceTest {
  @Test
  public void savesAndLoadsExtendedState() throws Exception {
    // Build a tiny scenario
    VnScenario scenario = new VnScenarioBuilder("persist_story")
        .addCharacter("alice", "Alice")
        .addBackground("room", "game/images/bg_room.png")
        .background("room")
        .dialogue("Alice", "Hello", "alice", "neutral", CharacterPosition.LEFT)
        .end()
        .build();

    // Prepare state
    VnState state = new VnState();
    state.setScenario(scenario);
    state.setCurrentBackgroundId("room");
    state.setCurrentNodeIndex(1);
    state.setVariable("flag", true);
    state.markNodeAsRead(0);
    state.markNodeAsRead(1);
    state.showCharacter(CharacterPosition.LEFT, "alice", "neutral", 15);
    state.pushCallStack(4);
    state.pushCallStack(7);
    state.setCharacterGlobalPositionEnabled("alice", true);
    state.setCharacterDefinedPosition("alice", CharacterPosition.CENTER);
    state.setSkipMode(true);
    state.setAutoPlayMode(true);
    state.setAutoPlayTimer(4321L);
    state.setUiHidden(true);
    state.setRpgState("rpg-snapshot");

    VnSettings s = state.getSettings();
    s.setTextSpeed(40);
    s.setBgmVolume(0.6f);
    s.setSfxVolume(0.5f);
    s.setVoiceVolume(0.4f);
    s.setAutoPlayDelay(2500);
    s.setSkipUnreadText(true);
    s.setSkipAfterChoices(false);
    s.setClickRevealBeforeAdvance(false);
    s.setPhysicsFixedStepMs(16);
    s.setPhysicsMaxSubSteps(8);
    s.setPhysicsDefaultFriction(0.33);
    s.setInputProfilePath("config/input/default.input");
    s.setInputProfileSerialized("move_up=W,move_down=S");
    s.setDisplayWidth(1440);
    s.setDisplayHeight(900);
    s.setAutoFitResolution(true);
    s.setAccessibilityTheme("highcontrast");
    s.setTextToSpeechEnabled(true);
    s.setUiFontScale(1.35);

    // Use temp dir for saves
    Path tmp = Files.createTempDirectory("vn_saves_test");
    VnSaveManager mgr = new VnSaveManager(tmp.toString());

    mgr.save(state, "slot1");
    VnSaveData data = mgr.load("slot1");

    VnState loaded = new VnState();
    loaded.setScenario(scenario);
    mgr.applyToState(data, loaded);

    assertEquals(state.getCurrentNodeIndex(), loaded.getCurrentNodeIndex());
    assertEquals(state.getCurrentBackgroundId(), loaded.getCurrentBackgroundId());
    assertEquals(state.isSkipMode(), loaded.isSkipMode());
    assertEquals(state.isAutoPlayMode(), loaded.isAutoPlayMode());
    assertEquals(state.getAutoPlayTimer(), loaded.getAutoPlayTimer());
    assertEquals(state.isUiHidden(), loaded.isUiHidden());

    assertEquals(state.getVariables().get("flag"), loaded.getVariables().get("flag"));
    assertEquals(state.getReadNodes(), loaded.getReadNodes());
    assertEquals(state.getCallStackSnapshot(), loaded.getCallStackSnapshot());
    assertTrue(loaded.isCharacterGlobalPositionEnabled("alice"));
    assertEquals(CharacterPosition.CENTER, loaded.getCharacterDefinedPosition("alice"));
    assertEquals("rpg-snapshot", loaded.getRpgState());

    assertEquals(s.getTextSpeed(), loaded.getSettings().getTextSpeed());
    assertEquals(s.getBgmVolume(), loaded.getSettings().getBgmVolume(), 0.001);
    assertEquals(s.getSfxVolume(), loaded.getSettings().getSfxVolume(), 0.001);
    assertEquals(s.getVoiceVolume(), loaded.getSettings().getVoiceVolume(), 0.001);
    assertEquals(s.getAutoPlayDelay(), loaded.getSettings().getAutoPlayDelay());
    assertEquals(s.isSkipUnreadText(), loaded.getSettings().isSkipUnreadText());
    assertEquals(s.isSkipAfterChoices(), loaded.getSettings().isSkipAfterChoices());
    assertEquals(s.isClickRevealBeforeAdvance(), loaded.getSettings().isClickRevealBeforeAdvance());
    assertEquals(s.getPhysicsFixedStepMs(), loaded.getSettings().getPhysicsFixedStepMs());
    assertEquals(s.getPhysicsMaxSubSteps(), loaded.getSettings().getPhysicsMaxSubSteps());
    assertEquals(s.getPhysicsDefaultFriction(), loaded.getSettings().getPhysicsDefaultFriction(), 0.0001);
    assertEquals(s.getInputProfilePath(), loaded.getSettings().getInputProfilePath());
    assertEquals(s.getInputProfileSerialized(), loaded.getSettings().getInputProfileSerialized());
    assertEquals(s.getDisplayWidth(), loaded.getSettings().getDisplayWidth());
    assertEquals(s.getDisplayHeight(), loaded.getSettings().getDisplayHeight());
    assertEquals(s.isAutoFitResolution(), loaded.getSettings().isAutoFitResolution());
    assertEquals(s.getAccessibilityTheme(), loaded.getSettings().getAccessibilityTheme());
    assertEquals(s.isTextToSpeechEnabled(), loaded.getSettings().isTextToSpeechEnabled());
    assertEquals(s.getUiFontScale(), loaded.getSettings().getUiFontScale(), 0.0001);

    // Visible characters
    assertTrue(loaded.getVisibleCharacters().containsKey(CharacterPosition.LEFT));
    assertEquals("alice", loaded.getVisibleCharacters().get(CharacterPosition.LEFT).getCharacterId());
    assertEquals(15, loaded.getVisibleCharacters().get(CharacterPosition.LEFT).getLayerOrder());
  }

  @Test
  public void savesAndLoadsDisplaySlotsAtSamePosition() throws Exception {
    VnScenario scenario = new VnScenarioBuilder("slot_persist_story")
        .addCharacter("body", "Body")
        .addCharacter("head", "Head")
        .end()
        .build();

    VnState state = new VnState();
    state.setScenario(scenario);
    state.showCharacter(CharacterPosition.CENTER, "body", "neutral", 0, "body");
    state.showCharacter(CharacterPosition.CENTER, "head", "neutral", 10, "head");

    Path tmp = Files.createTempDirectory("vn_slot_saves_test");
    VnSaveManager mgr = new VnSaveManager(tmp.toString());
    mgr.save(state, "slot_display");
    VnSaveData data = mgr.load("slot_display");

    VnState loaded = new VnState();
    loaded.setScenario(scenario);
    mgr.applyToState(data, loaded);

    assertEquals(2, loaded.getVisibleCharacters().size());
    CharacterPosition bodySlot = loaded.getDisplaySlotPosition("body");
    CharacterPosition headSlot = loaded.getDisplaySlotPosition("head");
    assertTrue(bodySlot != null && headSlot != null);
    assertEquals(CharacterPosition.CENTER, bodySlot.getBasePosition());
    assertEquals(CharacterPosition.CENTER, headSlot.getBasePosition());
    assertEquals("body", loaded.getVisibleCharacters().get(bodySlot).getCharacterId());
    assertEquals("head", loaded.getVisibleCharacters().get(headSlot).getCharacterId());
    assertEquals(10, loaded.getVisibleCharacters().get(headSlot).getLayerOrder());
  }

  @Test
  public void saveDuringExpressionTransitionClampsToTargetExpression() throws Exception {
    VnScenario scenario = new VnScenarioBuilder("transition_save")
        .addCharacterWithExpressions("lily", "Lily", "neutral.png", "talking.png")
        .end()
        .build();
    VnState state = new VnState();
    state.setScenario(scenario);
    state.showCharacter(CharacterPosition.CENTER, "lily", "neutral");
    assertTrue(state.setCharacterExpression("lily", "talking", 240));
    assertTrue(state.getExpressionTransition("lily") != null);

    Path tmp = Files.createTempDirectory("vn_transition_save_test");
    VnSaveManager manager = new VnSaveManager(tmp.toString());
    manager.save(state, "during_transition");

    VnState loaded = new VnState();
    loaded.setScenario(scenario);
    manager.applyToState(manager.load("during_transition"), loaded);

    assertEquals("talking", loaded.getCharacterExpression("lily"));
    assertTrue(loaded.getExpressionTransition("lily") == null);
  }
}
