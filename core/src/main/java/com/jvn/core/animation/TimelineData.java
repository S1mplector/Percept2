package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight runtime representation of a named animation timeline.
 *
 * <p>Each timeline contains one or more entity {@link Track}s, where each
 * track maps animatable {@link Property} channels to ordered lists of
 * {@link Keyframe}s. Additionally, timelines may contain {@link AudioCue}s
 * and {@link EventCue}s that fire at specific times during playback.</p>
 *
 * <p>This is the data bridge between the Puppeteer editor and the VNS
 * runtime (via {@code @external jes_timeline <name>}). At runtime,
 * {@link TimelineRunner} drives these data objects against a live scene.</p>
 *
 * <h2>Structure</h2>
 * <pre>
 * TimelineData
 *  ├── name, durationMs, looping
 *  ├── Track[] (one per entity or camera)
 *  │    └── Property → Keyframe[]
 *  ├── AudioCue[] (sorted by time)
 *  └── EventCue[] (sorted by time)
 * </pre>
 *
 * @see TimelineRunner
 * @see TimelineDataParser
 * @see TimelineRegistry
 */
public class TimelineData {

    /**
     * Animatable properties that can be keyframed on a track.
     * Entity properties map to {@link com.jvn.core.scene2d.Entity2D} fields;
     * camera properties map to {@link SceneAccessor} hooks.
     */
    public enum Property {
        X, Y, Z, PIVOT_X, PIVOT_Y, ROTATION, SCALE_X, SCALE_Y, ALPHA, VISIBILITY,
        CAMERA_X, CAMERA_Y, CAMERA_ZOOM
    }

    /**
     * A discrete event cue on the timeline (expression change, dialogue
     * marker, script call, etc.). Fired exactly once when the playhead
     * crosses the cue time.
     *
     * @see SceneAccessor#onEventCue(String, java.util.Map)
     */
    public static class EventCue {
        /** The absolute time (ms) at which this cue fires. */
        private final double timeMs;
        /** The event type identifier (e.g. "expression", "script_call"). */
        private final String type;
        /** Immutable key-value payload describing the event. */
        private final java.util.Map<String, String> payload;

        public EventCue(double timeMs, String type, java.util.Map<String, String> payload) {
            this.timeMs = Math.max(0.0, timeMs);
            this.type = type == null ? "" : type.trim();
            this.payload = payload != null
                ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(payload))
                : Collections.emptyMap();
        }

        public double getTimeMs() { return timeMs; }
        public String getType() { return type; }
        public java.util.Map<String, String> getPayload() { return payload; }
        public String getPayloadValue(String key) { return payload.getOrDefault(key, ""); }
    }

    /**
     * An audio cue that triggers playback of a sound/music track at a
     * specific point in the timeline.
     *
     * @see SceneAccessor#playAudioCue(String, String, double, boolean, double)
     */
    public static class AudioCue {
        /** Absolute time (ms) at which this audio cue fires. */
        private final double timeMs;
        /** Asset path to the audio file. */
        private final String trackPath;
        /** Audio channel name (e.g. "music", "sound"). */
        private final String channel;
        /** Playback volume [0, 1]. */
        private final double volume;
        /** Whether to loop the audio track. */
        private final boolean loop;
        /** Fade-in duration in milliseconds. */
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

    /**
     * A single keyframe representing a property value at a specific time.
     *
     * <p>The easing and interpolation mode determine how the value
     * transitions from the previous keyframe to this one. For
     * {@link Easing.Type#CUSTOM}, explicit cubic-bezier parameters
     * are stored in the easing spec.</p>
     */
    public static class Keyframe {
        /** Absolute time (ms) of this keyframe. */
        private final double timeMs;
        /** The property value at this keyframe. */
        private final double value;
        /** Easing spec used for the segment ending at this keyframe. */
        private final EasingSpec easingSpec;
        /** Interpolation mode for the segment ending at this keyframe. */
        private final Easing.Interpolation interpolation;

        public Keyframe(double timeMs, double value, Easing.Type easing) {
            this(timeMs, value, easing, Easing.Interpolation.TWEEN);
        }

        public Keyframe(double timeMs, double value, Easing.Type easing, Easing.Interpolation interpolation) {
            this(timeMs, value, EasingSpec.of(easing), interpolation);
        }

        public Keyframe(
            double timeMs,
            double value,
            Easing.Type easing,
            Easing.Interpolation interpolation,
            double[] easingParams
        ) {
            this(timeMs, value, EasingSpec.of(easing, easingParams), interpolation);
        }

        public Keyframe(
            double timeMs,
            double value,
            EasingSpec easingSpec,
            Easing.Interpolation interpolation
        ) {
            this.timeMs = timeMs;
            this.value = value;
            this.easingSpec = easingSpec != null ? easingSpec : EasingSpec.of(Easing.Type.LINEAR);
            this.interpolation = interpolation != null ? interpolation : Easing.Interpolation.TWEEN;
        }

        public double getTimeMs() { return timeMs; }
        public double getValue() { return value; }
        public Easing.Type getEasing() { return easingSpec.getType(); }
        public EasingSpec getEasingSpec() { return easingSpec; }
        public Easing.Interpolation getInterpolation() { return interpolation; }
        public boolean hasEasingParams() { return easingSpec.hasParameters(); }
        public double[] getEasingParams() { return easingSpec.getParameters(); }
        public boolean hasBezierParams() {
            return getEasing() == Easing.Type.CUSTOM
                && hasEasingParams()
                && getEasingParams().length == 4;
        }
        public double[] getBezierParams() {
            if (!hasBezierParams()) return null;
            return getEasingParams();
        }
    }

    /**
     * A named entity track containing keyframed property channels.
     *
     * <p>Each track is identified by an entity name (or the special
     * {@code "__camera__"} sentinel for camera properties). Property
     * values can be interpolated at any time via
     * {@link #getValueAt(Property, double)}.</p>
     */
    public static class Track {
        /** The scene entity name this track controls. */
        private final String entityName;
        /** Property channels mapped to their ordered keyframe lists. */
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
                    double eased = Easing.applyInterpolation(
                        b.getEasingSpec(), b.getInterpolation(), t);
                    return Easing.lerp(a.getValue(), b.getValue(), eased);
                }
            }
            return list.get(list.size() - 1).getValue();
        }

        private static double getDefaultValue(Property prop) {
            return switch (prop) {
                case SCALE_X, SCALE_Y, ALPHA, VISIBILITY, CAMERA_ZOOM -> 1.0;
                default -> 0.0;
            };
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Timeline-level fields and accessors
    // ──────────────────────────────────────────────────────────────────────

    /** Human-readable timeline name (used as lookup key in {@link TimelineRegistry}). */
    private final String name;

    /** Total duration of the timeline in milliseconds. */
    private final double durationMs;

    /** Ordered list of entity/camera tracks. */
    private final List<Track> tracks = new ArrayList<>();

    /** Audio cues scheduled during playback. */
    private final List<AudioCue> audioCues = new ArrayList<>();

    /** Event cues scheduled during playback. */
    private final List<EventCue> eventCues = new ArrayList<>();

    /** Whether the timeline wraps around when it reaches the end. */
    private boolean looping = false;

    /**
     * Construct a new timeline.
     *
     * @param name       the timeline name
     * @param durationMs total duration in milliseconds
     */
    public TimelineData(String name, double durationMs) {
        this.name = name;
        this.durationMs = durationMs;
    }

    /** @return the timeline name */
    public String getName() { return name; }

    /** @return the total duration in milliseconds */
    public double getDurationMs() { return durationMs; }

    /** @return {@code true} if the timeline loops */
    public boolean isLooping() { return looping; }

    /** Set whether the timeline loops. */
    public void setLooping(boolean looping) { this.looping = looping; }

    /** Add an entity/camera track to the timeline. */
    public void addTrack(Track track) {
        if (track != null) tracks.add(track);
    }

    /** @return an unmodifiable view of all tracks */
    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }

    /** Add an audio cue (ignored if the track path is blank). */
    public void addAudioCue(AudioCue cue) {
        if (cue != null && !cue.getTrackPath().isBlank()) audioCues.add(cue);
    }

    /** @return an unmodifiable view of all audio cues */
    public List<AudioCue> getAudioCues() {
        return Collections.unmodifiableList(audioCues);
    }

    /** Add an event cue (ignored if the type is blank). */
    public void addEventCue(EventCue cue) {
        if (cue != null && !cue.getType().isBlank()) eventCues.add(cue);
    }

    /** @return an unmodifiable view of all event cues */
    public List<EventCue> getEventCues() {
        return Collections.unmodifiableList(eventCues);
    }

    /**
     * Find a track by entity name.
     *
     * @param entityName the entity name to look up
     * @return the matching track, or {@code null}
     */
    public Track getTrack(String entityName) {
        for (Track t : tracks) {
            if (t.getEntityName().equals(entityName)) return t;
        }
        return null;
    }
}
