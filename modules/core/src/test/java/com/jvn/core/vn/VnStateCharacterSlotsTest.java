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
    // During movement we keep previous expression, then switch in place.
    assertEquals("neutral", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());

    state.updateCharacterAnimations(400);
    assertEquals("smile", state.getVisibleCharacters().get(CharacterPosition.RIGHT).getExpression());
    assertEquals(CharacterPosition.RIGHT, state.getCharacterDefinedPosition("codel"));
  }

  @Test
  void showCharacterAnimatedExpressionOnlySwitchIsInstantAndDoesNotRestartVisualAnimation() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    VnState.CharacterVisual visual = state.getCharacterVisual(CharacterPosition.CENTER);
    assertNotNull(visual);
    visual.setImmediate(0.8, 42.0, -6.0);

    state.showCharacterAnimated(CharacterPosition.CENTER, "john", "talking");

    VnState.CharacterSlot slot = state.getVisibleCharacters().get(CharacterPosition.CENTER);
    assertNotNull(slot);
    assertEquals("talking", slot.getExpression());
    assertEquals(0.8, visual.getAlpha(), 0.001);
    assertEquals(42.0, visual.getOffsetX(), 0.001);
    assertEquals(-6.0, visual.getOffsetY(), 0.001);
    assertTrue(visual.isFinished());
    assertNull(state.getExpressionTransition("john"));
  }

  @Test
  void expressionOnlySwitchCanDisableTransition() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    assertTrue(state.setCharacterExpression("john", "talking", 0));

    assertEquals("talking", state.getCharacterExpression("john"));
    assertNull(state.getExpressionTransition("john"));
  }

  @Test
  void expressionTransitionCompletesAfterDuration() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    assertTrue(state.setCharacterExpression("john", "talking", 100));

    VnState.ExpressionTransition transition = state.getExpressionTransition("john");
    assertNotNull(transition);
    assertEquals(0.0, transition.getProgress(), 0.0001);

    state.updateCharacterAnimations(50);
    transition = state.getExpressionTransition("john");
    assertNotNull(transition);
    assertTrue(transition.getProgress() > 0.0 && transition.getProgress() < 1.0);

    state.updateCharacterAnimations(50);
    assertNull(state.getExpressionTransition("john"));
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

  @Test
  void showCharacterPreservesTimelineDisplacedOccupantAsDetached() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    state.recordTimelineDisplacement("john", 542.0, 0.0, true, false);
    state.showCharacter(CharacterPosition.CENTER, "lily", "neutral");

    assertEquals("lily", state.getVisibleCharacters().get(CharacterPosition.CENTER).getCharacterId());
    assertEquals(1, state.getDetachedCharacters().size());
    VnState.DetachedCharacterSlot detached = state.getDetachedCharacter("john");
    assertNotNull(detached);
    assertEquals(CharacterPosition.CENTER, detached.getBasePosition());
    assertEquals("john", detached.getSlot().getCharacterId());
    assertEquals("neutral", detached.getSlot().getExpression());
  }

  @Test
  void showCharacterReplacesOccupantWhenTimelineDisplacementIsSmall() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    state.recordTimelineDisplacement("john", 24.0, 0.0, true, false);
    state.showCharacter(CharacterPosition.CENTER, "lily", "neutral");

    assertEquals("lily", state.getVisibleCharacters().get(CharacterPosition.CENTER).getCharacterId());
    assertTrue(state.getDetachedCharacters().isEmpty());
  }

  @Test
  void timelineDisplacementUsesLatestAnimatedAxisInsteadOfLargestAbsoluteOffset() {
    VnState state = new VnState();

    state.recordTimelineDisplacement("john", 542.0, 6.0, true, true);
    state.recordTimelineDisplacement("john", -532.0, 0.0, true, false);

    VnState.TimelineDisplacement displacement = state.getTimelineDisplacement("john");
    assertNotNull(displacement);
    assertTrue(displacement.hasX());
    assertTrue(displacement.hasY());
    assertEquals(-532.0, displacement.getX(), 0.0001);
    assertEquals(6.0, displacement.getY(), 0.0001);
  }

  @Test
  void hideCharacterAnimatedCanRemoveDetachedCharacter() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    state.recordTimelineDisplacement("john", 542.0, 0.0, true, false);
    state.showCharacter(CharacterPosition.CENTER, "lily", "neutral");

    state.hideCharacterAnimated("john");
    state.updateCharacterAnimations(300);

    assertNull(state.getDetachedCharacter("john"));
    assertEquals("lily", state.getVisibleCharacters().get(CharacterPosition.CENTER).getCharacterId());
  }

  @Test
  void expressionOnlySwitchCanUpdateDetachedTimelineCharacter() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "john", "neutral");
    state.recordTimelineDisplacement("john", 542.0, 0.0, true, false);
    state.showCharacter(CharacterPosition.CENTER, "lily", "neutral");

    assertTrue(state.setCharacterExpression("john", "talking"));

    VnState.CharacterSlot lily = state.getVisibleCharacters().get(CharacterPosition.CENTER);
    assertNotNull(lily);
    assertEquals("lily", lily.getCharacterId());
    assertEquals("talking", state.getCharacterExpression("john"));
    VnState.DetachedCharacterSlot detached = state.getDetachedCharacter("john");
    assertNotNull(detached);
    assertEquals("talking", detached.getSlot().getExpression());
    assertNull(state.getExpressionTransition("john"));
  }

  @Test
  void displaySlotsAllowMultipleCharactersAtSameVisualPosition() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "body", "neutral", 0, "body");
    state.showCharacter(CharacterPosition.CENTER, "head", "neutral", 10, "head");

    assertEquals(2, state.getVisibleCharacters().size());
    CharacterPosition bodySlot = state.getDisplaySlotPosition("body");
    CharacterPosition headSlot = state.getDisplaySlotPosition("head");
    assertNotNull(bodySlot);
    assertNotNull(headSlot);
    assertEquals(CharacterPosition.CENTER, bodySlot.getBasePosition());
    assertEquals(CharacterPosition.CENTER, headSlot.getBasePosition());
    assertEquals("body", state.getVisibleCharacters().get(bodySlot).getCharacterId());
    assertEquals("head", state.getVisibleCharacters().get(headSlot).getCharacterId());
    assertEquals(10, state.getVisibleCharacters().get(headSlot).getLayerOrder());
  }

  @Test
  void displaySlotReplacementOnlyReplacesSameSlot() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "body", "neutral", 0, "body");
    state.showCharacter(CharacterPosition.CENTER, "head_a", "neutral", 10, "head");
    state.showCharacter(CharacterPosition.RIGHT, "head_b", "smile", 12, "head");

    assertEquals(2, state.getVisibleCharacters().size());
    assertNotNull(state.getDisplaySlotPosition("body"));
    CharacterPosition headSlot = state.getDisplaySlotPosition("head");
    assertNotNull(headSlot);
    assertEquals(CharacterPosition.RIGHT, headSlot.getBasePosition());
    assertEquals("head_b", state.getVisibleCharacters().get(headSlot).getCharacterId());
    assertEquals("smile", state.getVisibleCharacters().get(headSlot).getExpression());
  }

  @Test
  void displaySlotExpressionAndHideTargetOnlyThatInstance() {
    VnState state = new VnState();

    state.showCharacter(CharacterPosition.CENTER, "hero", "body", 0, "body");
    state.showCharacter(CharacterPosition.CENTER, "hero", "head", 10, "head");

    assertTrue(state.setCharacterExpression("hero", "head", "blink", 0, null));
    assertEquals("body", state.getCharacterExpression("hero", "body"));
    assertEquals("blink", state.getCharacterExpression("hero", "head"));

    state.hideDisplaySlotAnimated("head");
    state.updateCharacterAnimations(300);

    assertNotNull(state.getDisplaySlotPosition("body"));
    assertNull(state.getDisplaySlotPosition("head"));
  }
}
