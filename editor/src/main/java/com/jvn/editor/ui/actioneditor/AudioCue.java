package com.jvn.editor.ui.actioneditor;

public class AudioCue implements Comparable<AudioCue> {
    private double timeMs;
    private String audioFile;
    private String channel; // "music", "sound", "voice"
    private double volume = 1.0;
    private boolean fadeIn = false;
    private double fadeDurationMs = 0;

    public AudioCue(double timeMs, String audioFile, String channel) {
        this.timeMs = sanitizeNonNegativeFinite(timeMs, 0.0);
        this.audioFile = normalizeText(audioFile);
        this.channel = normalizeChannel(channel);
    }

    public double getTimeMs() { return timeMs; }
    public void setTimeMs(double timeMs) { this.timeMs = sanitizeNonNegativeFinite(timeMs, this.timeMs); }

    public String getAudioFile() { return audioFile; }
    public void setAudioFile(String audioFile) { this.audioFile = normalizeText(audioFile); }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = normalizeChannel(channel); }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = clamp01Finite(volume, this.volume); }

    public boolean isFadeIn() { return fadeIn; }
    public void setFadeIn(boolean fadeIn) { this.fadeIn = fadeIn; }

    public double getFadeDurationMs() { return fadeDurationMs; }
    public void setFadeDurationMs(double fadeDurationMs) {
        this.fadeDurationMs = sanitizeNonNegativeFinite(fadeDurationMs, this.fadeDurationMs);
    }

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

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeChannel(String channel) {
        String normalized = normalizeText(channel);
        return normalized.isBlank() ? "sound" : normalized;
    }

    private static double sanitizeNonNegativeFinite(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, value);
    }

    private static double clamp01Finite(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }
}
