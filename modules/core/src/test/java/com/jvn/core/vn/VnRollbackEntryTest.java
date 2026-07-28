package com.jvn.core.vn;

import com.jvn.core.vn.rollback.VnRollbackEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnRollbackEntryTest {
  @Test
  void applyToRestoresExtendedState() {
    VnScenario scenario = new VnScenarioBuilder("rollback_story")
        .addCharacter("hero", "Hero")
        .addBackground("room", "game/images/bg_room.png")
        .dialogue("Hero", "Line", "hero", "neutral", CharacterPosition.LEFT)
        .end()
        .build();

    VnState state = new VnState();
    state.setScenario(scenario);
    state.setCurrentNodeIndex(1);
    state.setCurrentBackgroundId("room");
    state.setVariable("flag", true);
    state.markNodeAsRead(0);
    state.showCharacter(CharacterPosition.LEFT, "hero", "smile", 20);
    state.pushCallStack(9);
    state.pushCallStack(12);
    state.setCharacterGlobalPositionEnabled("hero", true);
    state.setCharacterDefinedPosition("hero", CharacterPosition.RIGHT);
    state.setSkipMode(true);
    state.setAutoPlayMode(true);
    state.setAutoPlayTimer(777L);
    state.setUiHidden(true);

    VnRollbackEntry entry = VnRollbackEntry.capture(state, "Hero", "Line");

    // Mutate all captured fields.
    state.setCurrentNodeIndex(0);
    state.setCurrentBackgroundId("other");
    state.setVariable("flag", false);
    state.setReadNodes(java.util.Set.of());
    state.clearAllCharacters();
    state.clearCallStack();
    state.setGlobalPositionState(java.util.Set.of(), java.util.Map.of());
    state.setSkipMode(false);
    state.setAutoPlayMode(false);
    state.setAutoPlayTimer(0L);
    state.setUiHidden(false);

    entry.applyTo(state);

    assertEquals(1, state.getCurrentNodeIndex());
    assertEquals("room", state.getCurrentBackgroundId());
    assertEquals(true, state.getVariables().get("flag"));
    assertTrue(state.getReadNodes().contains(0));
    assertEquals("hero", state.getVisibleCharacters().get(CharacterPosition.LEFT).getCharacterId());
    assertEquals(20, state.getVisibleCharacters().get(CharacterPosition.LEFT).getLayerOrder());
    assertEquals(java.util.List.of(9, 12), state.getCallStackSnapshot());
    assertTrue(state.isCharacterGlobalPositionEnabled("hero"));
    assertEquals(CharacterPosition.RIGHT, state.getCharacterDefinedPosition("hero"));
    assertTrue(state.isSkipMode());
    assertTrue(state.isAutoPlayMode());
    assertEquals(777L, state.getAutoPlayTimer());
    assertTrue(state.isUiHidden());
  }

  @Test
  void applyToRestoresDisplaySlots() {
    VnState state = new VnState();
    state.showCharacter(CharacterPosition.CENTER, "body", "neutral", 0, "body");
    state.showCharacter(CharacterPosition.CENTER, "head", "neutral", 10, "head");

    VnRollbackEntry entry = VnRollbackEntry.capture(state, "narrator", "snapshot");
    state.clearAllCharacters();

    entry.applyTo(state);

    assertEquals(2, state.getVisibleCharacters().size());
    CharacterPosition bodySlot = state.getDisplaySlotPosition("body");
    CharacterPosition headSlot = state.getDisplaySlotPosition("head");
    assertTrue(bodySlot != null && headSlot != null);
    assertEquals(CharacterPosition.CENTER, bodySlot.getBasePosition());
    assertEquals(CharacterPosition.CENTER, headSlot.getBasePosition());
    assertEquals("body", state.getVisibleCharacters().get(bodySlot).getCharacterId());
    assertEquals("head", state.getVisibleCharacters().get(headSlot).getCharacterId());
    assertEquals(10, state.getVisibleCharacters().get(headSlot).getLayerOrder());
  }

  @Test
  void rollbackDuringExpressionTransitionRestoresTargetExpressionDeterministically() {
    VnState state = new VnState();
    state.showCharacter(CharacterPosition.CENTER, "lily", "neutral");
    assertTrue(state.setCharacterExpression("lily", "talking", 240));
    assertTrue(state.getExpressionTransition("lily") != null);

    VnRollbackEntry entry = VnRollbackEntry.capture(state, "Lily", "Line");
    state.setCharacterExpression("lily", "neutral", 0);

    entry.applyTo(state);

    assertEquals("talking", state.getCharacterExpression("lily"));
    assertNull(state.getExpressionTransition("lily"));
  }
}
