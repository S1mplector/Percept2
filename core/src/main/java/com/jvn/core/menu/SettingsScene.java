package com.jvn.core.menu;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.VnSettings;

public class SettingsScene implements Scene {
  private final VnSettings settings;
  private final AudioFacade audio; // optional, to apply volumes live
  private int selected = 0;

  public SettingsScene(VnSettings settings) { this(settings, null); }

  public SettingsScene(VnSettings settings, AudioFacade audio) {
    this.settings = settings;
    this.audio = audio;
  }

  public VnSettings model() { return settings; }
  public int itemCount() { return 6; }

  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    int count = itemCount();
    selected = (selected + delta + count) % count;
  }
  public void setSelected(int idx) {
    int count = itemCount();
    if (idx < 0) idx = 0;
    if (idx >= count) idx = count - 1;
    selected = idx;
  }

  public void adjustCurrent(int delta) {
    switch (selected) {
      case 0 -> settings.setTextSpeed(settings.getTextSpeed() + delta);
      case 1 -> {
        settings.setBgmVolume(settings.getBgmVolume() + delta * 0.05f);
        if (audio != null) audio.setBgmVolume(settings.getBgmVolume());
      }
      case 2 -> {
        settings.setSfxVolume(settings.getSfxVolume() + delta * 0.05f);
        if (audio != null) audio.setSfxVolume(settings.getSfxVolume());
      }
      case 3 -> {
        settings.setVoiceVolume(settings.getVoiceVolume() + delta * 0.05f);
        if (audio != null) audio.setVoiceVolume(settings.getVoiceVolume());
      }
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
  public void update(long deltaMs) { }

  public void setValueByIndex(int idx, double value01) {
    double v = Math.max(0.0, Math.min(1.0, value01));
    switch (idx) {
      case 0 -> {
        // text speed 10..120 ms
        int min = 10, max = 120;
        int val = (int) Math.round(min + v * (max - min));
        settings.setTextSpeed(val);
      }
      case 1 -> settings.setBgmVolume((float) v);
      case 2 -> settings.setSfxVolume((float) v);
      case 3 -> settings.setVoiceVolume((float) v);
      case 4 -> {
        long min = 500, max = 5000;
        long val = Math.round(min + v * (max - min));
        settings.setAutoPlayDelay(val);
      }
      case 5 -> settings.setSkipUnreadText(v >= 0.5);
      default -> {}
    }
    // Live-apply volumes
    if (audio != null) {
      audio.setBgmVolume(settings.getBgmVolume());
      audio.setSfxVolume(settings.getSfxVolume());
      audio.setVoiceVolume(settings.getVoiceVolume());
    }
  }
}
