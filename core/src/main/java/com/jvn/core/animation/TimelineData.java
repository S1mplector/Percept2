package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight runtime representation of a named animation timeline.
 * Each timeline contains entity tracks, where each track maps properties
 * to keyframe lists. This is the data bridge between the Puppeteer editor
 * and the VNS runtime via {@code @external jes_timeline <name>}.
 */
public class TimelineData {

    public enum Property {
        X, Y, Z, PIVOT_X, PIVOT_Y, ROTATION, SCALE_X, SCALE_Y, ALPHA,
        CAMERA_X, CAMERA_Y, CAMERA_ZOOM
    }

    public static class AudioCue {
        private final double timeMs;
        private final String trackPath;
        private final String channel;
        private final double volume;
        private final boolean loop;
        private final double fadeInMs;

        public AudioCue(
            double timeMs,
            String trackPath,
            String channel,
            double volume,
            boolean loop,
            double fadeInMs
        ) {
            this.timeMs = Math.max(0.0, timeMs);
            this.trackPath = trackPath == null ? "" : trackPath.trim();
            this.channel = channel == null ? "sound" : channel.trim();
            this.volume = clamp01(volume);
            this.loop = loop;
            this.fadeInMs = Math.max(0.0, fadeInMs);
        }

        public double getTimeMs() { return timeMs; }
        public String getTrackPath() { return trackPath; }
        public String getChannel() { return channel; }
        public double getVolume() { return volume; }
        public boolean isLoop() { return loop; }
        public double getFadeInMs() { return fadeInMs; }

        private static double clamp01(double v) {
            if (v < 0.0) return 0.0;
            if (v > 1.0) return 1.0;
            return v;
        }
    }

    public static class Keyframe {
        private final double timeMs;
        private final double value;
        private final Easing.Type easing;

        public Keyframe(double timeMs, double value, Easing.Type easing) {
            this.timeMs = timeMs;
            this.value = value;
            this.easing = easing != null ? easing : Easing.Type.LINEAR;
        }

        public double getTimeMs() { return timeMs; }
        public double getValue() { return value; }
        public Easing.Type getEasing() { return easing; }
    }

    public static class Track {
        private final String entityName;
        private final Map<Property, List<Keyframe>> keyframes = new LinkedHashMap<>();

        public Track(String entityName) {
            this.entityName = entityName;
        }

        public String getEntityName() { return entityName; }

        public void addKeyframe(Property prop, Keyframe kf) {
            keyframes.computeIfAbsent(prop, k -> new ArrayList<>()).add(kf);
        }

        public List<Keyframe> getKeyframes(Property prop) {
            List<Keyframe> list = keyframes.get(prop);
            return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
        }

        public boolean hasKeyframes(Property prop) {
            List<Keyframe> list = keyframes.get(prop);
            return list != null && !list.isEmpty();
        }

        public Map<Property, List<Keyframe>> getAllKeyframes() {
            return Collections.unmodifiableMap(keyframes);
        }

        /**
         * Interpolate the value of a property at a given time using keyframes and easing.
         */
        public double getValueAt(Property prop, double timeMs) {
            List<Keyframe> list = keyframes.get(prop);
            if (list == null || list.isEmpty()) return getDefaultValue(prop);
            if (list.size() == 1) return list.get(0).getValue();

            // Before first keyframe
            if (timeMs <= list.get(0).getTimeMs()) return list.get(0).getValue();
            // After last keyframe
            if (timeMs >= list.get(list.size() - 1).getTimeMs()) return list.get(list.size() - 1).getValue();

            // Find surrounding keyframes
            for (int i = 0; i < list.size() - 1; i++) {
                Keyframe a = list.get(i);
                Keyframe b = list.get(i + 1);
                if (timeMs >= a.getTimeMs() && timeMs <= b.getTimeMs()) {
                    double span = b.getTimeMs() - a.getTimeMs();
                    if (span <= 0) return b.getValue();
                    double t = (timeMs - a.getTimeMs()) / span;
                    double eased = Easing.apply(b.getEasing(), t);
                    return Easing.lerp(a.getValue(), b.getValue(), eased);
                }
            }
            return list.get(list.size() - 1).getValue();
        }

        private static double getDefaultValue(Property prop) {
            return switch (prop) {
                case SCALE_X, SCALE_Y, CAMERA_ZOOM -> 1.0;
                case ALPHA -> 1.0;
                default -> 0.0;
            };
        }
    }

    private final String name;
    private final double durationMs;
    private final List<Track> tracks = new ArrayList<>();
    private final List<AudioCue> audioCues = new ArrayList<>();
    private boolean looping = false;

    public TimelineData(String name, double durationMs) {
        this.name = name;
        this.durationMs = durationMs;
    }

    public String getName() { return name; }
    public double getDurationMs() { return durationMs; }
    public boolean isLooping() { return looping; }
    public void setLooping(boolean looping) { this.looping = looping; }

    public void addTrack(Track track) {
        if (track != null) tracks.add(track);
    }

    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }

    public void addAudioCue(AudioCue cue) {
        if (cue != null && !cue.getTrackPath().isBlank()) audioCues.add(cue);
    }

    public List<AudioCue> getAudioCues() {
        return Collections.unmodifiableList(audioCues);
    }

    public Track getTrack(String entityName) {
        for (Track t : tracks) {
            if (t.getEntityName().equals(entityName)) return t;
        }
        return null;
    }
}
