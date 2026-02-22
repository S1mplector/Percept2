package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class VnStateCharacterSlotsTest {

  @Test
  void showCharacterKeepsSingleSlotPerCharacterId() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.LEFT, "codel", "neutral");
    state.showCharacter(CharacterPosition.CENTER, "codel", "smile");

    assertEquals(1, state.getVisibleCharacters().size());
    assertFalse(state.getVisibleCharacters().containsKey(CharacterPosition.LEFT));
    assertTrue(state.getVisibleCharacters().containsKey(CharacterPosition.CENTER));
    assertEquals("codel", state.getVisibleCharacters().get(CharacterPosition.CENTER).getCharacterId());
    assertEquals("smile", state.getVisibleCharacters().get(CharacterPosition.CENTER).getExpression());
  }

  @Test
  void showCharacterAnimatedKeepsSingleSlotPerCharacterId() {
    VnState state = new VnState();

    state.showCharacterAnimated(CharacterPosition.LEFT, "codel", "neutral");
    state.showCharacterAnimated(CharacterPosition.RIGHT, "codel", "happy");

    assertEquals(1, state.getVisibleCharacters().size());
    assertFalse(state.getVisibleCharacters().containsKey(CharacterPosition.LEFT));
    assertTrue(state.getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("codel", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());
    assertEquals("happy", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
    assertNull(state.getCharacterVisual(CharacterPosition.LEFT));
    assertNotNull(state.getCharacterVisual(CharacterPosition.RIGHT));
  }

  @Test
  void showingCharacterDoesNotRemoveOtherCharacters() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.LEFT, "codel", "neutral");
    state.showCharacter(CharacterPosition.RIGHT, "guide", "smile");
    state.showCharacter(CharacterPosition.CENTER, "codel", "surprised");

    assertEquals(2, state.getVisibleCharacters().size());
    assertFalse(state.getVisibleCharacters().containsKey(CharacterPosition.LEFT));
    assertTrue(state.getVisibleCharacters().containsKey(CharacterPosition.CENTER));
    assertTrue(state.getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    assertEquals("guide", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getCharacterId());
    assertEquals("codel", state.getVisibleCharacters().get(CharacterPosition.CENTER).getCharacterId());
  }

  @Test
  void globalPositionModeMovesAndThenFadesExpression() {
    VnState state = new VnState();
    state.setCharacterGlobalPositionEnabled("codel", true);

    state.showCharacterAnimated(CharacterPosition.CENTER, "codel", "neutral");
    state.showCharacterAnimated(CharacterPosition.RIGHT, "codel", "smile");

    assertEquals(1, state.getVisibleCharacters().size());
    assertFalse(state.getVisibleCharacters().containsKey(CharacterPosition.CENTER));
    assertTrue(state.getVisibleCharacters().containsKey(CharacterPosition.RIGHT));
    // During movement we keep previous expression, then switch with fade.
    assertEquals("neutral", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());

    state.updateCharacterAnimations(400);
    assertEquals("smile", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
    assertEquals(CharacterPosition.RIGHT, state.getCharacterDefinedPosition("codel"));
  }

  @Test
  void showCharacterSupportsExplicitLayerOrder() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.LEFT, "codel", "neutral", 42);

    assertEquals(42, state.getVisibleCharacters().get(CharacterPosition.LEFT).getLayerOrder());
  }

  @Test
  void showCharacterUsesPositionBasedDefaultLayerOrderWhenNotProvided() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.LEFT, "a", "neutral");
    state.showCharacter(CharacterPosition.RIGHT, "b", "neutral");

    assertEquals(-10, state.getVisibleCharacters().get(CharacterPosition.LEFT).getLayerOrder());
    assertEquals(10, state.getVisibleCharacters().get(CharacterPosition.RIGHT).getLayerOrder());
  }
}
