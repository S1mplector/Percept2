package com.jvn.core.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputBindingProfileTest {

  @Test
  void serializeAndRestoreBindings() {
    Input input = new Input();
    ActionMap map = new ActionMap(input);
    map.bindKey("jump", "Space")
        .bindMouse("fire", 1)
        .bindGamepadButton("dash", 0, "A");

    ActionBindingProfile profile = map.toProfile();
    String serialized = profile.serialize();

    ActionBindingProfile restored = ActionBindingProfile.deserialize(serialized);
    ActionMap map2 = new ActionMap(input);
    map2.loadProfile(restored);

    assertEquals(profile.actions().size(), restored.actions().size());
    assertEquals(profile.serialize(), map2.toProfile().serialize());
    assertTrue(profile.serialize().contains("jump"));
    assertTrue(profile.serialize().contains("fire"));
    assertTrue(profile.serialize().contains("dash"));
  }
}
