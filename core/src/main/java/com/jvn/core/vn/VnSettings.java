package com.jvn.core.vn;

/**
 * Configuration settings for visual novel playback
 */
public class VnSettings {
  private int textSpeed = 30; // ms per character
  private float bgmVolume = 0.7f;
  private float sfxVolume = 0.8f;
  private float voiceVolume = 1.0f;
  private long autoPlayDelay = 2000; // ms to wait before auto-advancing
  private boolean skipUnreadText = false;
  private boolean skipAfterChoices = false;

  public int getTextSpeed() { return textSpeed; }
  public void setTextSpeed(int speed) { this.textSpeed = Math.max(1, Math.min(speed, 200)); }

  public float getBgmVolume() { return bgmVolume; }
  public void setBgmVolume(float volume) { this.bgmVolume = Math.max(0f, Math.min(volume, 1f)); }

  public float getSfxVolume() { return sfxVolume; }
  public void setSfxVolume(float volume) { this.sfxVolume = Math.max(0f, Math.min(volume, 1f)); }

  public float getVoiceVolume() { return voiceVolume; }
  public void setVoiceVolume(float volume) { this.voiceVolume = Math.max(0f, Math.min(volume, 1f)); }

  public long getAutoPlayDelay() { return autoPlayDelay; }
  public void setAutoPlayDelay(long delay) { this.autoPlayDelay = Math.max(500, delay); }

  public boolean isSkipUnreadText() { return skipUnreadText; }
  public void setSkipUnreadText(boolean skip) { this.skipUnreadText = skip; }

  public boolean isSkipAfterChoices() { return skipAfterChoices; }
  public void setSkipAfterChoices(boolean skip) { this.skipAfterChoices = skip; }

  public VnSettings copy() {
    VnSettings copy = new VnSettings();
    copy.textSpeed = this.textSpeed;
    copy.bgmVolume = this.bgmVolume;
    copy.sfxVolume = this.sfxVolume;
    copy.voiceVolume = this.voiceVolume;
    copy.autoPlayDelay = this.autoPlayDelay;
    copy.skipUnreadText = this.skipUnreadText;
    copy.skipAfterChoices = this.skipAfterChoices;
    return copy;
  }
}
