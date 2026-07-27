package com.jvn.core.menu;

import com.jvn.core.audio.AudioFacade;

/**
 * Shared audio handoff rules for transitions between menu and gameplay scenes.
 */
final class MenuAudioLifecycle {
  private MenuAudioLifecycle() {}

  /**
   * End title/menu BGM before gameplay enters. The gameplay scene may start or
   * cross-fade its own track from silence during {@code onEnter()}.
   */
  static void beginGameplay(AudioFacade audio) {
    if (audio != null) audio.stopBgm();
  }
}
