package com.jvn.editor.ui.actioneditor;

public class AudioCue implements Comparable<AudioCue> {
    private double timeMs;
    private String audioFile;
    private String channel; // "music", "sound", "voice"
    private double volume = 1.0;
    private boolean fadeIn = false;
    private double fadeDurationMs = 0;

    public AudioCue(double timeMs, String audioFile, String channel) {
        this.timeMs = Math.max(0, timeMs);
        this.audioFile = audioFile != null ? audioFile : "";
        this.channel = channel != null ? channel : "sound";
    }

    public double getTimeMs() { return timeMs; }
    public void setTimeMs(double timeMs) { this.timeMs = Math.max(0, timeMs); }

    public String getAudioFile() { return audioFile; }
    public void setAudioFile(String audioFile) { this.audioFile = audioFile != null ? audioFile : ""; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel != null ? channel : "sound"; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = Math.max(0, Math.min(1.0, volume)); }

    public boolean isFadeIn() { return fadeIn; }
    public void setFadeIn(boolean fadeIn) { this.fadeIn = fadeIn; }

    public double getFadeDurationMs() { return fadeDurationMs; }
    public void setFadeDurationMs(double fadeDurationMs) { this.fadeDurationMs = Math.max(0, fadeDurationMs); }

    public AudioCue copy() {
        AudioCue c = new AudioCue(timeMs, audioFile, channel);
        c.volume = volume;
        c.fadeIn = fadeIn;
        c.fadeDurationMs = fadeDurationMs;
        return c;
    }

    @Override
    public int compareTo(AudioCue other) {
        return Double.compare(this.timeMs, other.timeMs);
    }

    @Override
    public String toString() {
        return String.format("AudioCue[t=%.0fms, %s, %s]", timeMs, channel, audioFile);
    }
}
