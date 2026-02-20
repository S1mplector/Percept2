package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

public class Keyframe implements Comparable<Keyframe> {
    private double timeMs;
    private double value;
    private Easing.Type easing;
    private double cx1 = 0.25, cy1 = 0.1, cx2 = 0.25, cy2 = 1.0;

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

    public double getCx1() { return cx1; }
    public double getCy1() { return cy1; }
    public double getCx2() { return cx2; }
    public double getCy2() { return cy2; }
    public void setBezierParams(double cx1, double cy1, double cx2, double cy2) {
        this.cx1 = cx1; this.cy1 = cy1; this.cx2 = cx2; this.cy2 = cy2;
    }
    public double[] getBezierParams() { return new double[]{ cx1, cy1, cx2, cy2 }; }

    public Keyframe copy() {
        Keyframe k = new Keyframe(timeMs, value, easing);
        k.setBezierParams(cx1, cy1, cx2, cy2);
        return k;
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
