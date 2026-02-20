package com.jvn.core.animation;

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

    private final TimelineData timeline;
    private final SceneAccessor scene;
    private double elapsedMs = 0;
    private boolean finished = false;

    public TimelineRunner(TimelineData timeline, SceneAccessor scene) {
        this.timeline = timeline;
        this.scene = scene;
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

        elapsedMs += deltaMs;

        if (elapsedMs >= timeline.getDurationMs()) {
            if (timeline.isLooping()) {
                elapsedMs = elapsedMs % timeline.getDurationMs();
            } else {
                elapsedMs = timeline.getDurationMs();
                finished = true;
            }
        }

        applyFrame(elapsedMs);
    }

    /**
     * Apply the timeline state at a specific time to all entities.
     */
    public void applyFrame(double timeMs) {
        for (TimelineData.Track track : timeline.getTracks()) {
            Entity2D entity = scene.findEntity(track.getEntityName());
            if (entity == null) continue;

            if (track.hasKeyframes(TimelineData.Property.X) || track.hasKeyframes(TimelineData.Property.Y)) {
                double x = track.getValueAt(TimelineData.Property.X, timeMs);
                double y = track.getValueAt(TimelineData.Property.Y, timeMs);
                entity.setPosition(x, y);
            }
            if (track.hasKeyframes(TimelineData.Property.ROTATION)) {
                double rot = track.getValueAt(TimelineData.Property.ROTATION, timeMs);
                entity.setRotationDeg(rot);
            }
            if (track.hasKeyframes(TimelineData.Property.SCALE_X) || track.hasKeyframes(TimelineData.Property.SCALE_Y)) {
                double sx = track.getValueAt(TimelineData.Property.SCALE_X, timeMs);
                double sy = track.getValueAt(TimelineData.Property.SCALE_Y, timeMs);
                entity.setScale(sx, sy);
            }
            if (track.hasKeyframes(TimelineData.Property.ALPHA)) {
                double alpha = track.getValueAt(TimelineData.Property.ALPHA, timeMs);
                applyAlpha(entity, alpha);
            }
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
}
