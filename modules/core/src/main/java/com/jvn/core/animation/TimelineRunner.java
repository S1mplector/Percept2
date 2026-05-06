package com.jvn.core.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.graphics.Camera2D;

/**
 * Plays back a {@link TimelineData} against a live scene via
 * {@link SceneAccessor}.
 *
 * <p>Create one runner per playback session and call
 * {@link #update(long)} once per frame. The runner interpolates
 * every track's properties at the current elapsed time, applies
 * them to entities in the scene, and triggers audio/event cues
 * as the playhead passes their scheduled times.</p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct with a timeline and scene accessor.</li>
 *   <li>Call {@link #update(long)} each frame with the frame delta.</li>
 *   <li>Query {@link #isFinished()} to detect completion (non-looping).</li>
 * </ol>
 *
 * @see TimelineData
 * @see SceneAccessor
 */
public class TimelineRunner {

    /** Epsilon for floating-point time comparisons. */
    private static final double EPS = 1e-6;

    /** The timeline data being played. */
    private final TimelineData timeline;

    /** The scene adapter that receives property updates and cue callbacks. */
    private final SceneAccessor scene;

    /** Audio cues sorted by time for efficient triggering during playback. */
    private final List<TimelineData.AudioCue> sortedAudioCues;

    /** Event cues sorted by time for efficient triggering during playback. */
    private final List<TimelineData.EventCue> sortedEventCues;

    /** Current playhead position in milliseconds. */
    private double elapsedMs = 0;

    /** {@code true} once the playhead reaches the end (non-looping only). */
    private boolean finished = false;
    private Runnable onFinished;

    /**
     * Construct a runner for the given timeline.
     *
     * @param timeline the animation data to play
     * @param scene    the scene adapter for entity/camera/audio access
     */
    public TimelineRunner(TimelineData timeline, SceneAccessor scene) {
        this.timeline = timeline;
        this.scene = scene;
        this.sortedAudioCues = new ArrayList<>(timeline.getAudioCues());
        this.sortedAudioCues.sort(Comparator.comparingDouble(TimelineData.AudioCue::getTimeMs));
        this.sortedEventCues = new ArrayList<>(timeline.getEventCues());
        this.sortedEventCues.sort(Comparator.comparingDouble(TimelineData.EventCue::getTimeMs));
    }

    /** @return the timeline being played */
    public TimelineData getTimeline() { return timeline; }

    /** @return {@code true} if playback has completed (always {@code false} for looping timelines) */
    public boolean isFinished() { return finished; }

    /** @return the current playhead position in milliseconds */
    public double getElapsedMs() { return elapsedMs; }

    public void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    /**
     * Advance the animation by {@code deltaMs} milliseconds, trigger any
     * audio/event cues in the elapsed interval, and apply interpolated
     * property values to all tracked entities.
     *
     * @param deltaMs frame delta in milliseconds
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
            triggerEventInterval(prevElapsed, nextElapsed, 1.0, timeline.isLooping());
            elapsedMs = 0.0;
            if (!timeline.isLooping()) markFinished();
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
            if (nextElapsed >= duration - EPS) markFinished();
        }
        applyFrame(elapsedMs);
    }

    /**
     * Apply the timeline state at a specific time to all entities.
     * This can be called independently for scrubbing / seeking.
     *
     * @param timeMs the absolute playback time in milliseconds
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
            for (var entry : track.getAllCustomKeyframes().entrySet()) {
                String propertyKey = entry.getKey();
                if (propertyKey == null || propertyKey.isBlank()) continue;
                double defaultValue = resolveCustomPropertyDefault(track.getEntityName(), entity, propertyKey);
                double value = track.getCustomValueAt(propertyKey, timeMs, defaultValue);
                scene.applyCustomProperty(track.getEntityName(), propertyKey, value);
            }

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
            if (track.hasKeyframes(TimelineData.Property.VISIBILITY)) {
                double visible = track.getValueAt(TimelineData.Property.VISIBILITY, timeMs);
                entity.setVisible(visible >= 0.5);
            }

        }
    }

    /** Trigger audio cues whose time falls within the given absolute interval. */
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

    /** Trigger event cues whose time falls within the given absolute interval. */
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

    /** Fire event cues within a single-cycle time window. */
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

    /** Fire audio cues within a single-cycle time window. */
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

    /** Apply an alpha value to the entity, dispatching by concrete type. */
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
        return entity.getOriginX();
    }

    private static double getPivotY(Entity2D entity) {
        return entity.getOriginY();
    }

    private static void setPivot(Entity2D entity, double pivotX, double pivotY) {
        entity.setOrigin(clampPivot(pivotX), clampPivot(pivotY));
    }

    private static double clampPivot(double value) {
        if (!Double.isFinite(value)) return 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private double resolveCustomPropertyDefault(String target, Entity2D entity, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return 0.0;
        if ("__camera__".equals(target)) {
            var definition = Camera2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        if (entity == null) {
            var definition = Entity2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        return entity.readCustomProperty(propertyKey);
    }

    private void markFinished() {
        if (finished) return;
        finished = true;
        Runnable callback = onFinished;
        onFinished = null;
        if (callback != null) {
            callback.run();
        }
    }
}
