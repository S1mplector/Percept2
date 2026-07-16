package com.jvn.core.audio;

/** Thread-safe master/channel gain model shared by audio backends. */
public final class AudioMix {
  private volatile float master = 1f;
  private volatile float bgm = 0.7f;
  private volatile float sfx = 0.8f;
  private volatile float voice = 1f;
  private volatile boolean muted;

  public float masterVolume() { return master; }
  public float bgmVolume() { return bgm; }
  public float sfxVolume() { return sfx; }
  public float voiceVolume() { return voice; }
  public boolean muted() { return muted; }

  public void setMasterVolume(float value) { master = clamp(value); }
  public void setBgmVolume(float value) { bgm = clamp(value); }
  public void setSfxVolume(float value) { sfx = clamp(value); }
  public void setVoiceVolume(float value) { voice = clamp(value); }
  public void setMuted(boolean value) { muted = value; }

  public double effective(AudioChannel channel) {
    if (muted) return 0.0;
    float channelGain = switch (channel == null ? AudioChannel.MASTER : channel) {
      case BGM -> bgm;
      case SFX -> sfx;
      case VOICE -> voice;
      case MASTER -> 1f;
    };
    return (double) master * channelGain;
  }

  public static float clamp(float value) {
    if (!Float.isFinite(value) || value <= 0f) return 0f;
    return Math.min(1f, value);
  }
}
