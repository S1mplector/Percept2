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
  private boolean clickRevealBeforeAdvance = true;
  private long physicsFixedStepMs = 0; // 0 = variable
  private int physicsMaxSubSteps = 4;
  private double physicsDefaultFriction = 0.2;
  private String inputProfilePath = System.getProperty("user.home") + "/.jvn/input-bindings.properties";
  private String inputProfileSerialized = ""; // serialized ActionBindingProfile (optional)

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

  public boolean isClickRevealBeforeAdvance() { return clickRevealBeforeAdvance; }
  public void setClickRevealBeforeAdvance(boolean enabled) { this.clickRevealBeforeAdvance = enabled; }

  public long getPhysicsFixedStepMs() { return physicsFixedStepMs; }
  public void setPhysicsFixedStepMs(long ms) { this.physicsFixedStepMs = Math.max(0, ms); }

  public int getPhysicsMaxSubSteps() { return physicsMaxSubSteps; }
  public void setPhysicsMaxSubSteps(int steps) { this.physicsMaxSubSteps = Math.max(1, steps); }

  public double getPhysicsDefaultFriction() { return physicsDefaultFriction; }
  public void setPhysicsDefaultFriction(double friction) {
    if (Double.isNaN(friction) || Double.isInfinite(friction)) friction = 0;
    this.physicsDefaultFriction = Math.max(0.0, Math.min(1.0, friction));
  }

  public String getInputProfilePath() { return inputProfilePath; }
  public void setInputProfilePath(String path) {
    if (path != null && !path.isBlank()) this.inputProfilePath = path;
  }

  public String getInputProfileSerialized() { return inputProfileSerialized; }
  public void setInputProfileSerialized(String serialized) {
    this.inputProfileSerialized = serialized == null ? "" : serialized;
  }

  public VnSettings copy() {
    VnSettings copy = new VnSettings();
    copy.textSpeed = this.textSpeed;
    copy.bgmVolume = this.bgmVolume;
    copy.sfxVolume = this.sfxVolume;
    copy.voiceVolume = this.voiceVolume;
    copy.autoPlayDelay = this.autoPlayDelay;
    copy.skipUnreadText = this.skipUnreadText;
    copy.skipAfterChoices = this.skipAfterChoices;
    copy.clickRevealBeforeAdvance = this.clickRevealBeforeAdvance;
    copy.physicsFixedStepMs = this.physicsFixedStepMs;
    copy.physicsMaxSubSteps = this.physicsMaxSubSteps;
    copy.physicsDefaultFriction = this.physicsDefaultFriction;
    copy.inputProfilePath = this.inputProfilePath;
    copy.inputProfileSerialized = this.inputProfileSerialized;
    return copy;
  }
}
