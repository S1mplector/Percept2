package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.List;

public class SplinePath {

    public static class Point {
        public final double x, y;
        public Point(double x, double y) { this.x = x; this.y = y; }
    }

    public static List<Point> catmullRom(List<Point> controlPoints, int segmentsPerSpan) {
        if (controlPoints == null || controlPoints.size() < 2) {
            return controlPoints != null ? new ArrayList<>(controlPoints) : new ArrayList<>();
        }

        List<Point> result = new ArrayList<>();
        int n = controlPoints.size();

        for (int i = 0; i < n - 1; i++) {
            Point p0 = controlPoints.get(Math.max(0, i - 1));
            Point p1 = controlPoints.get(i);
            Point p2 = controlPoints.get(Math.min(n - 1, i + 1));
            Point p3 = controlPoints.get(Math.min(n - 1, i + 2));

            for (int s = 0; s < segmentsPerSpan; s++) {
                double t = (double) s / segmentsPerSpan;
                double t2 = t * t;
                double t3 = t2 * t;

                double x = 0.5 * ((2 * p1.x)
                    + (-p0.x + p2.x) * t
                    + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2
                    + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);

                double y = 0.5 * ((2 * p1.y)
                    + (-p0.y + p2.y) * t
                    + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2
                    + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);

                result.add(new Point(x, y));
            }
        }

        result.add(controlPoints.get(n - 1));
        return result;
    }

    public static Point interpolate(List<Point> controlPoints, double t) {
        if (controlPoints == null || controlPoints.isEmpty()) return new Point(0, 0);
        if (controlPoints.size() == 1) return controlPoints.get(0);
        if (t <= 0) return controlPoints.get(0);
        if (t >= 1) return controlPoints.get(controlPoints.size() - 1);

        int n = controlPoints.size() - 1;
        double scaled = t * n;
        int i = Math.min((int) scaled, n - 1);
        double local = scaled - i;

        Point p0 = controlPoints.get(Math.max(0, i - 1));
        Point p1 = controlPoints.get(i);
        Point p2 = controlPoints.get(Math.min(n, i + 1));
        Point p3 = controlPoints.get(Math.min(n, i + 2));

        double local2 = local * local;
        double local3 = local2 * local;

        double x = 0.5 * ((2 * p1.x)
            + (-p0.x + p2.x) * local
            + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * local2
            + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * local3);

        double y = 0.5 * ((2 * p1.y)
            + (-p0.y + p2.y) * local
            + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * local2
            + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * local3);

        return new Point(x, y);
    }

    public static List<Point> buildControlPoints(EntityTrack track, double totalDurationMs) {
        List<Keyframe> xFrames = track.getKeyframes(PropertyType.X);
        List<Keyframe> yFrames = track.getKeyframes(PropertyType.Y);

        if (xFrames.isEmpty() && yFrames.isEmpty()) return new ArrayList<>();

        java.util.Set<Double> timesSet = new java.util.TreeSet<>();
        for (Keyframe kf : xFrames) timesSet.add(kf.getTimeMs());
        for (Keyframe kf : yFrames) timesSet.add(kf.getTimeMs());
        List<Double> times = new ArrayList<>(timesSet);

        List<Point> points = new ArrayList<>();
        for (double t : times) {
            double x = track.getValueAt(PropertyType.X, t);
            double y = track.getValueAt(PropertyType.Y, t);
            points.add(new Point(x, y));
        }
        return points;
    }
}
