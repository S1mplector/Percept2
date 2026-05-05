package com.jvn.fx;

import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionBindingProfileStore;
import com.jvn.core.input.ActionMap;
import com.jvn.core.input.InputActions;
import com.jvn.core.vn.VnSettingsStore;

/**
 * Helper to load action bindings from settings/user file.
 */
final class FxLauncherBindings {
  private FxLauncherBindings() {}

  static ActionMap load() {
    var settings = new VnSettingsStore().load();
    ActionBindingProfile profile;
    if (settings.getInputProfileSerialized() != null && !settings.getInputProfileSerialized().isBlank()) {
      profile = ActionBindingProfile.deserialize(settings.getInputProfileSerialized());
    } else {
      profile = InputActions.defaultProfile();
      try {
        ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
        // if a saved profile exists, prefer it
        profile = store.load();
      } catch (Exception ignored) {}
    }
    ActionMap map = new ActionMap(new com.jvn.core.input.Input()); // placeholder Input; replaced by engine in launcher
    map.loadProfile(profile);
    return map;
  }
}
