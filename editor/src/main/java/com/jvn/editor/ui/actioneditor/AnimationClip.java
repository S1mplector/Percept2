package com.jvn.editor.ui.actioneditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.animation.Easing;

/**
 * Reusable animation clip — a named snippet of keyframe data that can be
 * saved from a track selection and later applied (instanced) onto another
 * entity/group at a given playhead time, optionally with duration scaling.
 * <p>
 * Clips are persisted as simple property files under
 * {@code config/puppeteer/clips/} in the project directory.
 */
public class AnimationClip {
    private String name;
    private double durationMs;
    private final Map<PropertyType, List<ClipKeyframe>> channels = new LinkedHashMap<>();

    /** A single keyframe inside a clip, with time relative to clip start (0 = clip start). */
    public static class ClipKeyframe {
        private final double offsetMs;
        private final double value;
        private final Easing.Type easing;
        private final Easing.Interpolation interpolation;
        private final double[] bezierParams;

        public ClipKeyframe(double offsetMs, double value, Easing.Type easing) {
            this(offsetMs, value, easing, Easing.Interpolation.TWEEN, null);
        }

        public ClipKeyframe(
            double offsetMs,
            double value,
            Easing.Type easing,
            Easing.Interpolation interpolation,
            double[] bezierParams
        ) {
            this.offsetMs = offsetMs;
            this.value = value;
            this.easing = easing != null ? easing : Easing.Type.LINEAR;
            this.interpolation = interpolation != null ? interpolation : Easing.Interpolation.TWEEN;
            this.bezierParams = (bezierParams != null && bezierParams.length == 4)
                ? new double[]{bezierParams[0], bezierParams[1], bezierParams[2], bezierParams[3]}
                : null;
        }

        public double getOffsetMs() { return offsetMs; }
        public double getValue() { return value; }
        public Easing.Type getEasing() { return easing; }
        public Easing.Interpolation getInterpolation() { return interpolation; }
        public boolean hasBezierParams() { return bezierParams != null && bezierParams.length == 4; }
        public double[] getBezierParams() {
            if (!hasBezierParams()) return null;
            return new double[]{bezierParams[0], bezierParams[1], bezierParams[2], bezierParams[3]};
        }
    }

    public AnimationClip(String name) {
        this.name = name != null ? name : "Untitled Clip";
        this.durationMs = 0;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : "Untitled Clip"; }
    public double getDurationMs() { return durationMs; }

    public Map<PropertyType, List<ClipKeyframe>> getChannels() {
        return Collections.unmodifiableMap(channels);
    }

    /**
     * Capture a range from an EntityTrack as clip data.
     *
     * @param track the source track
     * @param startMs range start in timeline time
     * @param endMs range end in timeline time
     */
    public void captureFromTrack(EntityTrack track, double startMs, double endMs) {
        channels.clear();
        this.durationMs = Math.max(0, endMs - startMs);
        for (PropertyType prop : PropertyType.values()) {
            List<Keyframe> src = track.getKeyframes(prop);
            if (src.isEmpty()) continue;
            List<ClipKeyframe> clipKfs = new ArrayList<>();
            for (Keyframe kf : src) {
                if (kf.getTimeMs() >= startMs - 0.5 && kf.getTimeMs() <= endMs + 0.5) {
                    clipKfs.add(new ClipKeyframe(
                        kf.getTimeMs() - startMs,
                        kf.getValue(),
                        kf.getEasing(),
                        kf.getInterpolation(),
                        kf.getEasing() == Easing.Type.CUSTOM ? kf.getBezierParams() : null));
                }
            }
            if (!clipKfs.isEmpty()) {
                channels.put(prop, clipKfs);
            }
        }
    }

    /**
     * Apply this clip onto a target track at the given insertion time,
     * optionally scaling duration.
     *
     * @param target the track to apply keyframes onto
     * @param insertTimeMs playhead position where clip starts
     * @param durationScale 1.0 = original speed, 2.0 = double duration, etc.
     */
    public void applyToTrack(EntityTrack target, double insertTimeMs, double durationScale) {
        double scale = Math.max(0.01, durationScale);
        for (Map.Entry<PropertyType, List<ClipKeyframe>> entry : channels.entrySet()) {
            PropertyType prop = entry.getKey();
            for (ClipKeyframe ckf : entry.getValue()) {
                double time = insertTimeMs + ckf.getOffsetMs() * scale;
                Keyframe kf = new Keyframe(time, ckf.getValue(), ckf.getEasing(), ckf.getInterpolation());
                if (ckf.getEasing() == Easing.Type.CUSTOM && ckf.hasBezierParams()) {
                    double[] bezier = ckf.getBezierParams();
                    kf.setBezierParams(bezier[0], bezier[1], bezier[2], bezier[3]);
                }
                target.addKeyframe(prop, kf);
            }
        }
    }

    // ---- Serialization ----

    /**
     * Serialize this clip to a simple properties-style string.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("clip.name=").append(name).append("\n");
        sb.append("clip.durationMs=").append(formatNum(durationMs)).append("\n");
        for (Map.Entry<PropertyType, List<ClipKeyframe>> entry : channels.entrySet()) {
            String propKey = entry.getKey().getCode();
            List<ClipKeyframe> kfs = entry.getValue();
            sb.append("clip.channel.").append(propKey).append(".count=").append(kfs.size()).append("\n");
            for (int i = 0; i < kfs.size(); i++) {
                ClipKeyframe ckf = kfs.get(i);
                String prefix = "clip.channel." + propKey + "." + i;
                sb.append(prefix).append(".offset=").append(formatNum(ckf.getOffsetMs())).append("\n");
                sb.append(prefix).append(".value=").append(formatNum(ckf.getValue())).append("\n");
                sb.append(prefix).append(".easing=").append(ckf.getEasing().name().toLowerCase()).append("\n");
                sb.append(prefix).append(".interp=").append(ckf.getInterpolation().name().toLowerCase()).append("\n");
                if (ckf.getEasing() == Easing.Type.CUSTOM && ckf.hasBezierParams()) {
                    double[] bezier = ckf.getBezierParams();
                    sb.append(prefix).append(".bezier=")
                        .append(formatNum(bezier[0])).append(",")
                        .append(formatNum(bezier[1])).append(",")
                        .append(formatNum(bezier[2])).append(",")
                        .append(formatNum(bezier[3])).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Deserialize a clip from properties-style text.
     */
    public static AnimationClip deserialize(String text) {
        if (text == null || text.isBlank()) return new AnimationClip("Empty");
        Map<String, String> props = new LinkedHashMap<>();
        for (String line : text.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                props.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        AnimationClip clip = new AnimationClip(props.getOrDefault("clip.name", "Untitled"));
        clip.durationMs = parseDouble(props.get("clip.durationMs"), 0);

        for (PropertyType pt : PropertyType.values()) {
            String countKey = "clip.channel." + pt.getCode() + ".count";
            int count = parseInt(props.get(countKey), 0);
            if (count <= 0) continue;
            List<ClipKeyframe> kfs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String prefix = "clip.channel." + pt.getCode() + "." + i;
                double offset = parseDouble(props.get(prefix + ".offset"), 0);
                double value = parseDouble(props.get(prefix + ".value"), 0);
                Easing.Type easing = parseEasing(props.get(prefix + ".easing"));
                Easing.Interpolation interpolation = parseInterpolation(props.get(prefix + ".interp"));
                double[] bezier = parseBezierParams(props.get(prefix + ".bezier"));
                kfs.add(new ClipKeyframe(offset, value, easing, interpolation, bezier));
            }
            clip.channels.put(pt, kfs);
        }
        return clip;
    }

    /**
     * Save this clip to a file.
     */
    public void saveTo(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, serialize());
    }

    /**
     * Load a clip from a file.
     */
    public static AnimationClip loadFrom(Path path) throws IOException {
        return deserialize(Files.readString(path));
    }

    private static String formatNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.0001) return Long.toString(Math.round(v));
        return String.format("%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s); } catch (Exception e) { return def; }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private static Easing.Type parseEasing(String s) {
        if (s == null || s.isBlank()) return Easing.Type.LINEAR;
        try { return Easing.Type.valueOf(s.toUpperCase()); } catch (Exception e) { return Easing.Type.LINEAR; }
    }

    private static Easing.Interpolation parseInterpolation(String s) {
        if (s == null || s.isBlank()) return Easing.Interpolation.TWEEN;
        try {
            return Easing.Interpolation.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return Easing.Interpolation.TWEEN;
        }
    }

    private static double[] parseBezierParams(String s) {
        if (s == null || s.isBlank()) return null;
        String[] parts = s.split(",");
        if (parts.length != 4) return null;
        try {
            return new double[] {
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()),
                Double.parseDouble(parts[3].trim())
            };
        } catch (Exception e) {
            return null;
        }
    }
}
