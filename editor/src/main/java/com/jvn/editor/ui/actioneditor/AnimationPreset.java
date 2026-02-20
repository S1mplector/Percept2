package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnimationPreset {
    private final String name;
    private final String category;
    private final Map<PropertyType, Keyframe[]> keyframes;

    private AnimationPreset(String name, String category, Map<PropertyType, Keyframe[]> keyframes) {
        this.name = name;
        this.category = category;
        this.keyframes = keyframes;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }

    public void applyTo(EntityTrack track, double startTimeMs) {
        for (var entry : keyframes.entrySet()) {
            PropertyType prop = entry.getKey();
            for (Keyframe template : entry.getValue()) {
                Keyframe kf = new Keyframe(
                    startTimeMs + template.getTimeMs(),
                    template.getValue(),
                    template.getEasing()
                );
                track.addKeyframe(prop, kf);
            }
        }
    }

    private static Map<PropertyType, Keyframe[]> kf(Object... args) {
        Map<PropertyType, Keyframe[]> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            map.put((PropertyType) args[i], (Keyframe[]) args[i + 1]);
        }
        return map;
    }

    private static Keyframe k(double t, double v) { return new Keyframe(t, v); }
    private static Keyframe k(double t, double v, Easing.Type e) { return new Keyframe(t, v, e); }

    public static final AnimationPreset FADE_IN = new AnimationPreset("Fade In", "Entrance", kf(
        PropertyType.ALPHA, new Keyframe[]{k(0, 0), k(500, 1, Easing.Type.EASE_OUT_QUAD)}
    ));

    public static final AnimationPreset FADE_OUT = new AnimationPreset("Fade Out", "Exit", kf(
        PropertyType.ALPHA, new Keyframe[]{k(0, 1), k(500, 0, Easing.Type.EASE_IN_QUAD)}
    ));

    public static final AnimationPreset SLIDE_FROM_LEFT = new AnimationPreset("Slide From Left", "Entrance", kf(
        PropertyType.X, new Keyframe[]{k(0, -300), k(400, 0, Easing.Type.EASE_OUT_CUBIC)},
        PropertyType.ALPHA, new Keyframe[]{k(0, 0), k(200, 1, Easing.Type.EASE_OUT_QUAD)}
    ));

    public static final AnimationPreset SLIDE_FROM_RIGHT = new AnimationPreset("Slide From Right", "Entrance", kf(
        PropertyType.X, new Keyframe[]{k(0, 300), k(400, 0, Easing.Type.EASE_OUT_CUBIC)},
        PropertyType.ALPHA, new Keyframe[]{k(0, 0), k(200, 1, Easing.Type.EASE_OUT_QUAD)}
    ));

    public static final AnimationPreset SLIDE_FROM_BOTTOM = new AnimationPreset("Slide From Bottom", "Entrance", kf(
        PropertyType.Y, new Keyframe[]{k(0, 200), k(400, 0, Easing.Type.EASE_OUT_CUBIC)},
        PropertyType.ALPHA, new Keyframe[]{k(0, 0), k(200, 1, Easing.Type.EASE_OUT_QUAD)}
    ));

    public static final AnimationPreset BOUNCE_IN = new AnimationPreset("Bounce In", "Entrance", kf(
        PropertyType.SCALE_X, new Keyframe[]{k(0, 0.3), k(200, 1.1, Easing.Type.EASE_OUT_QUAD), k(350, 0.9, Easing.Type.EASE_IN_OUT_QUAD), k(500, 1.0, Easing.Type.EASE_OUT_BOUNCE)},
        PropertyType.SCALE_Y, new Keyframe[]{k(0, 0.3), k(200, 1.1, Easing.Type.EASE_OUT_QUAD), k(350, 0.9, Easing.Type.EASE_IN_OUT_QUAD), k(500, 1.0, Easing.Type.EASE_OUT_BOUNCE)},
        PropertyType.ALPHA, new Keyframe[]{k(0, 0), k(150, 1)}
    ));

    public static final AnimationPreset SHAKE = new AnimationPreset("Shake", "Emphasis", kf(
        PropertyType.X, new Keyframe[]{k(0, 0), k(80, -15), k(160, 15), k(240, -10), k(320, 10), k(400, -5), k(480, 5), k(550, 0, Easing.Type.EASE_OUT_QUAD)}
    ));

    public static final AnimationPreset PULSE = new AnimationPreset("Pulse", "Emphasis", kf(
        PropertyType.SCALE_X, new Keyframe[]{k(0, 1), k(250, 1.15, Easing.Type.EASE_IN_OUT_QUAD), k(500, 1, Easing.Type.EASE_IN_OUT_QUAD)},
        PropertyType.SCALE_Y, new Keyframe[]{k(0, 1), k(250, 1.15, Easing.Type.EASE_IN_OUT_QUAD), k(500, 1, Easing.Type.EASE_IN_OUT_QUAD)}
    ));

    public static final AnimationPreset SPIN = new AnimationPreset("Spin", "Emphasis", kf(
        PropertyType.ROTATION, new Keyframe[]{k(0, 0), k(600, 360, Easing.Type.EASE_IN_OUT_CUBIC)}
    ));

    public static final AnimationPreset ZOOM_OUT = new AnimationPreset("Zoom Out", "Exit", kf(
        PropertyType.SCALE_X, new Keyframe[]{k(0, 1), k(400, 0, Easing.Type.EASE_IN_CUBIC)},
        PropertyType.SCALE_Y, new Keyframe[]{k(0, 1), k(400, 0, Easing.Type.EASE_IN_CUBIC)},
        PropertyType.ALPHA, new Keyframe[]{k(0, 1), k(400, 0, Easing.Type.EASE_IN_QUAD)}
    ));

    public static final AnimationPreset FLOAT = new AnimationPreset("Float", "Loop", kf(
        PropertyType.Y, new Keyframe[]{k(0, 0), k(1000, -15, Easing.Type.EASE_IN_OUT_SINE), k(2000, 0, Easing.Type.EASE_IN_OUT_SINE)}
    ));

    public static final AnimationPreset BREATHE = new AnimationPreset("Breathe", "Loop", kf(
        PropertyType.SCALE_X, new Keyframe[]{k(0, 1), k(1500, 1.05, Easing.Type.EASE_IN_OUT_SINE), k(3000, 1, Easing.Type.EASE_IN_OUT_SINE)},
        PropertyType.SCALE_Y, new Keyframe[]{k(0, 1), k(1500, 1.05, Easing.Type.EASE_IN_OUT_SINE), k(3000, 1, Easing.Type.EASE_IN_OUT_SINE)}
    ));

    public static final AnimationPreset[] ALL = {
        FADE_IN, FADE_OUT,
        SLIDE_FROM_LEFT, SLIDE_FROM_RIGHT, SLIDE_FROM_BOTTOM,
        BOUNCE_IN, ZOOM_OUT,
        SHAKE, PULSE, SPIN,
        FLOAT, BREATHE
    };
}
