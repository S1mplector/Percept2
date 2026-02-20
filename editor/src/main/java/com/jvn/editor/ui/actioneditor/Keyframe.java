package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

public class Keyframe implements Comparable<Keyframe> {
    private double timeMs;
    private double value;
    private Easing.Type easing;

    public Keyframe(double timeMs, double value) {
        this(timeMs, value, Easing.Type.LINEAR);
    }

    public Keyframe(double timeMs, double value, Easing.Type easing) {
        this.timeMs = Math.max(0, timeMs);
        this.value = value;
        this.easing = easing != null ? easing : Easing.Type.LINEAR;
    }

    public double getTimeMs() { return timeMs; }
    public void setTimeMs(double timeMs) { this.timeMs = Math.max(0, timeMs); }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public Easing.Type getEasing() { return easing; }
    public void setEasing(Easing.Type easing) { 
        this.easing = easing != null ? easing : Easing.Type.LINEAR; 
    }

    public Keyframe copy() {
        return new Keyframe(timeMs, value, easing);
    }

    @Override
    public int compareTo(Keyframe other) {
        return Double.compare(this.timeMs, other.timeMs);
    }

    @Override
    public String toString() {
        return String.format("Keyframe[t=%.0fms, v=%.2f, %s]", timeMs, value, easing);
    }
}
