package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

public class Keyframe implements Comparable<Keyframe> {
    private double timeMs;
    private double value;
    private EasingSpec easingSpec;
    private Easing.Interpolation interpolation = Easing.Interpolation.TWEEN;

    public Keyframe(double timeMs, double value) {
        this(timeMs, value, Easing.Type.LINEAR);
    }

    public Keyframe(double timeMs, double value, Easing.Type easing) {
        this(timeMs, value, easing, Easing.Interpolation.TWEEN);
    }

    public Keyframe(double timeMs, double value, Easing.Type easing, Easing.Interpolation interpolation) {
        this(timeMs, value, EasingSpec.of(easing), interpolation);
    }

    public Keyframe(double timeMs, double value, EasingSpec easingSpec, Easing.Interpolation interpolation) {
        this.timeMs = sanitizeNonNegativeFinite(timeMs, 0.0);
        this.value = sanitizeFinite(value, 0.0);
        this.easingSpec = easingSpec != null ? easingSpec : EasingSpec.of(Easing.Type.LINEAR);
        this.interpolation = interpolation != null ? interpolation : Easing.Interpolation.TWEEN;
    }

    public double getTimeMs() { return timeMs; }
    public void setTimeMs(double timeMs) { this.timeMs = sanitizeNonNegativeFinite(timeMs, 0.0); }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = sanitizeFinite(value, this.value); }

    public Easing.Type getEasing() { return easingSpec.getType(); }
    public EasingSpec getEasingSpec() { return easingSpec; }
    public void setEasing(Easing.Type easing) { 
        Easing.Type resolved = easing != null ? easing : Easing.Type.LINEAR;
        if (easingSpec != null && easingSpec.getType() == resolved) return;
        this.easingSpec = EasingSpec.of(resolved);
    }
    public void setEasingSpec(EasingSpec easingSpec) {
        this.easingSpec = easingSpec != null ? easingSpec : EasingSpec.of(Easing.Type.LINEAR);
    }

    public Easing.Interpolation getInterpolation() { return interpolation; }
    public void setInterpolation(Easing.Interpolation interpolation) {
        this.interpolation = interpolation != null ? interpolation : Easing.Interpolation.TWEEN;
    }

    public double getCx1() { return getBezierParams()[0]; }
    public double getCy1() { return getBezierParams()[1]; }
    public double getCx2() { return getBezierParams()[2]; }
    public double getCy2() { return getBezierParams()[3]; }
    public void setBezierParams(double cx1, double cy1, double cx2, double cy2) {
        this.easingSpec = EasingSpec.cubicBezier(cx1, cy1, cx2, cy2);
    }
    public boolean hasEasingParams() { return easingSpec.hasParameters(); }
    public double[] getEasingParams() { return easingSpec.getParameters(); }
    public double[] getBezierParams() {
        double[] params = Easing.coerceParameters(Easing.Type.CUSTOM,
            getEasing() == Easing.Type.CUSTOM ? easingSpec.getParameters() : null);
        return new double[]{ params[0], params[1], params[2], params[3] };
    }

    public Keyframe copy() {
        return new Keyframe(timeMs, value, easingSpec, interpolation);
    }

    private static double sanitizeFinite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double sanitizeNonNegativeFinite(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, value);
    }

    @Override
    public int compareTo(Keyframe other) {
        return Double.compare(this.timeMs, other.timeMs);
    }

    @Override
    public String toString() {
        return String.format("Keyframe[t=%.0fms, v=%.2f, %s, %s]",
            timeMs, value, interpolation, easingSpec);
    }
}
