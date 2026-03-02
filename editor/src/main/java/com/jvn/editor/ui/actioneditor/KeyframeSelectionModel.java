package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages multi-keyframe selection state for the timeline panel, including
 * marquee (box) selection, multi-select via Shift/Ctrl, and ripple-retime
 * operations.
 * <p>
 * This is a pure model class with no JavaFX dependencies so it can be
 * unit-tested without a running FX toolkit.
 */
public class KeyframeSelectionModel {

    /** Identifies a single keyframe within the project. */
    public record KeyframeRef(String entityName, PropertyType property, int keyframeIndex) {}

    private final Set<KeyframeRef> selected = new LinkedHashSet<>();
    private boolean rippleRetimeEnabled = false;

    /** Channel visibility filter — when non-empty, only listed categories are shown. */
    public enum ChannelCategory { TRANSFORM, CAMERA, AUDIO, EVENT }
    private final Set<ChannelCategory> visibleCategories = new LinkedHashSet<>();

    public KeyframeSelectionModel() {
        // All categories visible by default
        Collections.addAll(visibleCategories, ChannelCategory.values());
    }

    // ---- Selection ----

    public void select(KeyframeRef ref) {
        selected.add(ref);
    }

    public void deselect(KeyframeRef ref) {
        selected.remove(ref);
    }

    public void toggleSelect(KeyframeRef ref) {
        if (selected.contains(ref)) selected.remove(ref);
        else selected.add(ref);
    }

    public void clearSelection() {
        selected.clear();
    }

    public boolean isSelected(KeyframeRef ref) {
        return selected.contains(ref);
    }

    public Set<KeyframeRef> getSelected() {
        return Collections.unmodifiableSet(selected);
    }

    public int getSelectionCount() {
        return selected.size();
    }

    /**
     * Box / marquee selection: select all keyframes whose time falls within
     * [startMs, endMs] for tracks matching the given entity name (or all if null).
     *
     * @param project     the current project
     * @param entityName  filter to this entity, or null for all
     * @param startMs     left edge of the marquee in timeline ms
     * @param endMs       right edge of the marquee in timeline ms
     * @param additive    if true, add to existing selection; if false, replace
     */
    public void boxSelect(AnimationProject project, String entityName,
                          double startMs, double endMs, boolean additive) {
        if (!additive) selected.clear();
        double lo = Math.min(startMs, endMs);
        double hi = Math.max(startMs, endMs);

        for (EntityTrack track : project.getTracks()) {
            if (entityName != null && !track.getEntityName().equals(entityName)) continue;
            for (PropertyType prop : PropertyType.values()) {
                if (!isCategoryVisible(prop)) continue;
                List<Keyframe> kfs = track.getKeyframes(prop);
                for (int i = 0; i < kfs.size(); i++) {
                    double t = kfs.get(i).getTimeMs();
                    if (t >= lo && t <= hi) {
                        selected.add(new KeyframeRef(track.getEntityName(), prop, i));
                    }
                }
            }
        }
    }

    // ---- Ripple retime ----

    public boolean isRippleRetimeEnabled() { return rippleRetimeEnabled; }
    public void setRippleRetimeEnabled(boolean enabled) { this.rippleRetimeEnabled = enabled; }

    /**
     * Move all selected keyframes by deltaMs.  When ripple retime is enabled,
     * also shift all keyframes that come after the selection by the same delta.
     *
     * @param project  the current project
     * @param deltaMs  time shift in ms (positive = later, negative = earlier)
     * @param snap     snap increment in ms, or 0 for no snapping
     */
    public void moveSelected(AnimationProject project, double deltaMs, double snap) {
        if (selected.isEmpty()) return;

        // Collect the actual Keyframe objects for the selected refs
        List<Keyframe> toMove = new ArrayList<>();
        double latestSelectedTime = Double.NEGATIVE_INFINITY;

        for (KeyframeRef ref : selected) {
            EntityTrack track = project.getTrack(ref.entityName());
            if (track == null) continue;
            List<Keyframe> kfs = track.getKeyframes(ref.property());
            if (ref.keyframeIndex() < 0 || ref.keyframeIndex() >= kfs.size()) continue;
            Keyframe kf = kfs.get(ref.keyframeIndex());
            toMove.add(kf);
            if (kf.getTimeMs() > latestSelectedTime) latestSelectedTime = kf.getTimeMs();
        }

        for (Keyframe kf : toMove) {
            double newTime = kf.getTimeMs() + deltaMs;
            if (snap > 0) newTime = Math.round(newTime / snap) * snap;
            kf.setTimeMs(newTime);
        }

        // Ripple: shift all keyframes after the selection
        if (rippleRetimeEnabled && !toMove.isEmpty()) {
            for (EntityTrack track : project.getTracks()) {
                for (PropertyType prop : PropertyType.values()) {
                    List<Keyframe> kfs = track.getKeyframes(prop);
                    for (Keyframe kf : kfs) {
                        if (toMove.contains(kf)) continue;
                        if (kf.getTimeMs() > latestSelectedTime) {
                            double newTime = kf.getTimeMs() + deltaMs;
                            if (snap > 0) newTime = Math.round(newTime / snap) * snap;
                            kf.setTimeMs(newTime);
                        }
                    }
                }
            }
        }

        // Re-sort affected tracks
        for (KeyframeRef ref : selected) {
            EntityTrack track = project.getTrack(ref.entityName());
            if (track != null) track.sortKeyframes(ref.property());
        }
    }

    // ---- Channel visibility ----

    public Set<ChannelCategory> getVisibleCategories() {
        return Collections.unmodifiableSet(visibleCategories);
    }

    public void setVisibleCategory(ChannelCategory cat, boolean visible) {
        if (visible) visibleCategories.add(cat);
        else visibleCategories.remove(cat);
    }

    public boolean isCategoryVisible(ChannelCategory cat) {
        return visibleCategories.contains(cat);
    }

    public boolean isCategoryVisible(PropertyType prop) {
        ChannelCategory cat = categorize(prop);
        return cat == null || visibleCategories.contains(cat);
    }

    public static ChannelCategory categorize(PropertyType prop) {
        if (prop == null) return null;
        if (prop.isCameraProperty()) return ChannelCategory.CAMERA;
        return ChannelCategory.TRANSFORM;
    }
}
