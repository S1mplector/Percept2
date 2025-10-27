package com.jvn.core.menu;

import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnSettings;

public class SettingsScene implements Scene {
  private final VnSettings settings;
  private int selected = 0;

  public SettingsScene(VnSettings settings) {
    this.settings = settings;
  }

  public VnSettings model() { return settings; }

  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    int count = 6;
    selected = (selected + delta + count) % count;
  }

  public void adjustCurrent(int delta) {
    switch (selected) {
      case 0 -> settings.setTextSpeed(settings.getTextSpeed() + delta);
      case 1 -> settings.setBgmVolume(settings.getBgmVolume() + delta * 0.05f);
      case 2 -> settings.setSfxVolume(settings.getSfxVolume() + delta * 0.05f);
      case 3 -> settings.setVoiceVolume(settings.getVoiceVolume() + delta * 0.05f);
      case 4 -> settings.setAutoPlayDelay(settings.getAutoPlayDelay() + delta * 100L);
      case 5 -> settings.setSkipUnreadText(!settings.isSkipUnreadText());
      default -> {}
    }
  }

  public void toggleCurrent() {
    if (selected == 5) {
      settings.setSkipUnreadText(!settings.isSkipUnreadText());
    }
  }

  @Override
  public void update(long deltaMs) {
  }
}
