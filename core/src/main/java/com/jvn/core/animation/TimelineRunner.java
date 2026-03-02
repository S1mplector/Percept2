package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.jvn.core.scene2d.CharacterEntity2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Sprite2D;

/**
 * Applies a {@link TimelineData} to a live scene via {@link SceneAccessor}.
 * Create one per playback, call {@link #update(long)} each frame.
 * Query {@link #isFinished()} to know when the animation is complete.
 */
public class TimelineRunner {
    private static final double EPS = 1e-6;

    private final TimelineData timeline;
    private final SceneAccessor scene;
    private final List<TimelineData.AudioCue> sortedAudioCues;
    private final List<TimelineData.EventCue> sortedEventCues;
    private double elapsedMs = 0;
    private boolean finished = false;

    public TimelineRunner(TimelineData timeline, SceneAccessor scene) {
        this.timeline = timeline;
        this.scene = scene;
        this.sortedAudioCues = new ArrayList<>(timeline.getAudioCues());
        this.sortedAudioCues.sort(Comparator.comparingDouble(TimelineData.AudioCue::getTimeMs));
        this.sortedEventCues = new ArrayList<>(timeline.getEventCues());
        this.sortedEventCues.sort(Comparator.comparingDouble(TimelineData.EventCue::getTimeMs));
    }

    public TimelineData getTimeline() { return timeline; }
    public boolean isFinished() { return finished; }
    public double getElapsedMs() { return elapsedMs; }

    /**
     * Advance the animation by deltaMs milliseconds and apply property values
     * to all tracked entities in the scene.
     */
    public void update(long deltaMs) {
        if (finished) return;

        double duration = Math.max(0.0, timeline.getDurationMs());
        double safeDelta = Math.max(0L, deltaMs);
        if (safeDelta <= 0.0) {
            applyFrame(elapsedMs);
            return;
        }
        double prevElapsed = elapsedMs;
        double nextElapsed = prevElapsed + safeDelta;

        if (duration <= EPS) {
            triggerAudioInterval(prevElapsed, nextElapsed, 1.0, timeline.isLooping());
            elapsedMs = 0.0;
            if (!timeline.isLooping()) finished = true;
            applyFrame(elapsedMs);
            return;
        }

        if (timeline.isLooping()) {
            triggerAudioInterval(prevElapsed, nextElapsed, duration, true);
            triggerEventInterval(prevElapsed, nextElapsed, duration, true);
            elapsedMs = nextElapsed % duration;
        } else {
            double clampedNext = Math.min(nextElapsed, duration);
            triggerAudioInterval(prevElapsed, clampedNext, duration, false);
            triggerEventInterval(prevElapsed, clampedNext, duration, false);
            elapsedMs = clampedNext;
            if (nextElapsed >= duration - EPS) finished = true;
        }
        applyFrame(elapsedMs);
    }

    /**
     * Apply the timeline state at a specific time to all entities.
     */
    public void applyFrame(double timeMs) {
        for (TimelineData.Track track : timeline.getTracks()) {
            if (track.hasKeyframes(TimelineData.Property.CAMERA_X)) {
                scene.setCameraX(track.getValueAt(TimelineData.Property.CAMERA_X, timeMs));
            }
            if (track.hasKeyframes(TimelineData.Property.CAMERA_Y)) {
                scene.setCameraY(track.getValueAt(TimelineData.Property.CAMERA_Y, timeMs));
            }
            if (track.hasKeyframes(TimelineData.Property.CAMERA_ZOOM)) {
                scene.setCameraZoom(track.getValueAt(TimelineData.Property.CAMERA_ZOOM, timeMs));
            }

            Entity2D entity = scene.findEntity(track.getEntityName());
            if (entity == null) continue;

            if (track.hasKeyframes(TimelineData.Property.X) || track.hasKeyframes(TimelineData.Property.Y)) {
                double x = track.hasKeyframes(TimelineData.Property.X)
                    ? track.getValueAt(TimelineData.Property.X, timeMs)
                    : entity.getX();
                double y = track.hasKeyframes(TimelineData.Property.Y)
                    ? track.getValueAt(TimelineData.Property.Y, timeMs)
                    : entity.getY();
                entity.setPosition(x, y);
            }
            if (track.hasKeyframes(TimelineData.Property.Z)) {
                double z = track.getValueAt(TimelineData.Property.Z, timeMs);
                entity.setZ(z);
            }
            if (track.hasKeyframes(TimelineData.Property.PIVOT_X) || track.hasKeyframes(TimelineData.Property.PIVOT_Y)) {
                double pivotX = track.hasKeyframes(TimelineData.Property.PIVOT_X)
                    ? track.getValueAt(TimelineData.Property.PIVOT_X, timeMs)
                    : getPivotX(entity);
                double pivotY = track.hasKeyframes(TimelineData.Property.PIVOT_Y)
                    ? track.getValueAt(TimelineData.Property.PIVOT_Y, timeMs)
                    : getPivotY(entity);
                setPivot(entity, pivotX, pivotY);
            }
            if (track.hasKeyframes(TimelineData.Property.ROTATION)) {
                double rot = track.getValueAt(TimelineData.Property.ROTATION, timeMs);
                entity.setRotationDeg(rot);
            }
            if (track.hasKeyframes(TimelineData.Property.SCALE_X) || track.hasKeyframes(TimelineData.Property.SCALE_Y)) {
                double sx = track.hasKeyframes(TimelineData.Property.SCALE_X)
                    ? track.getValueAt(TimelineData.Property.SCALE_X, timeMs)
                    : entity.getScaleX();
                double sy = track.hasKeyframes(TimelineData.Property.SCALE_Y)
                    ? track.getValueAt(TimelineData.Property.SCALE_Y, timeMs)
                    : entity.getScaleY();
                entity.setScale(sx, sy);
            }
            if (track.hasKeyframes(TimelineData.Property.ALPHA)) {
                double alpha = track.getValueAt(TimelineData.Property.ALPHA, timeMs);
                applyAlpha(entity, alpha);
            }
        }
    }

    private void triggerAudioInterval(double startAbs, double endAbs, double duration, boolean looping) {
        if (sortedAudioCues.isEmpty() || endAbs + EPS < startAbs) return;

        if (!looping) {
            triggerAudioWindow(startAbs, endAbs, startAbs <= EPS);
            return;
        }

        long startCycle = (long) Math.floor(startAbs / duration);
        long endCycle = (long) Math.floor(endAbs / duration);
        for (long cycle = startCycle; cycle <= endCycle; cycle++) {
            double cycleBase = cycle * duration;
            double localStart = (cycle == startCycle) ? (startAbs - cycleBase) : 0.0;
            double localEnd = (cycle == endCycle) ? (endAbs - cycleBase) : duration;
            triggerAudioWindow(localStart, localEnd, localStart <= EPS);
        }
    }

    private void triggerEventInterval(double startAbs, double endAbs, double duration, boolean looping) {
        if (sortedEventCues.isEmpty() || endAbs + EPS < startAbs) return;

        if (!looping) {
            triggerEventWindow(startAbs, endAbs, startAbs <= EPS);
            return;
        }

        long startCycle = (long) Math.floor(startAbs / duration);
        long endCycle = (long) Math.floor(endAbs / duration);
        for (long cycle = startCycle; cycle <= endCycle; cycle++) {
            double cycleBase = cycle * duration;
            double localStart = (cycle == startCycle) ? (startAbs - cycleBase) : 0.0;
            double localEnd = (cycle == endCycle) ? (endAbs - cycleBase) : duration;
            triggerEventWindow(localStart, localEnd, localStart <= EPS);
        }
    }

    private void triggerEventWindow(double localStart, double localEnd, boolean includeStart) {
        for (TimelineData.EventCue cue : sortedEventCues) {
            if (cue == null || cue.getType().isBlank()) continue;
            double t = cue.getTimeMs();
            boolean inWindow = includeStart
                ? (t + EPS >= localStart && t <= localEnd + EPS)
                : (t > localStart + EPS && t <= localEnd + EPS);
            if (!inWindow) continue;
            scene.onEventCue(cue.getType(), cue.getPayload());
        }
    }

    private void triggerAudioWindow(double localStart, double localEnd, boolean includeStart) {
        for (TimelineData.AudioCue cue : sortedAudioCues) {
            if (cue == null || cue.getTrackPath().isBlank()) continue;
            double t = cue.getTimeMs();
            boolean inWindow = includeStart
                ? (t + EPS >= localStart && t <= localEnd + EPS)
                : (t > localStart + EPS && t <= localEnd + EPS);
            if (!inWindow) continue;
            scene.playAudioCue(
                cue.getTrackPath(),
                cue.getChannel(),
                cue.getVolume(),
                cue.isLoop(),
                cue.getFadeInMs()
            );
        }
    }

    private void applyAlpha(Entity2D entity, double alpha) {
        if (entity instanceof Sprite2D s) {
            s.setAlpha(alpha);
        } else if (entity instanceof Label2D l) {
            l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), alpha);
        } else if (entity instanceof Panel2D p) {
            p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), alpha);
        }
    }

    private static double getPivotX(Entity2D entity) {
        if (entity instanceof Sprite2D s) return s.getOriginX();
        if (entity instanceof CharacterEntity2D c) return c.getOriginX();
        return 0.0;
    }

    private static double getPivotY(Entity2D entity) {
        if (entity instanceof Sprite2D s) return s.getOriginY();
        if (entity instanceof CharacterEntity2D c) return c.getOriginY();
        return 0.0;
    }

    private static void setPivot(Entity2D entity, double pivotX, double pivotY) {
        double clampedX = clampPivot(pivotX);
        double clampedY = clampPivot(pivotY);
        if (entity instanceof Sprite2D s) {
            s.setOrigin(clampedX, clampedY);
        } else if (entity instanceof CharacterEntity2D c) {
            c.setOrigin(clampedX, clampedY);
        }
    }

    private static double clampPivot(double value) {
        if (!Double.isFinite(value)) return 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }
}
