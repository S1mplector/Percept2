package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical multi-keyframe selection and bulk retime model for the timeline.
 * Selection entries keep a direct reference to the keyframe instance so they
 * survive track sorting during drag and batch operations.
 */
public class KeyframeSelectionModel {

    /** Identifies a selected keyframe within the project. */
    public static final class KeyframeRef {
        private final String entityName;
        private final PropertyType property;
        private final Keyframe keyframe;

        public KeyframeRef(String entityName, PropertyType property, Keyframe keyframe) {
            this.entityName = entityName;
            this.property = property;
            this.keyframe = keyframe;
        }

        public String entityName() { return entityName; }
        public PropertyType property() { return property; }
        public Keyframe keyframe() { return keyframe; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof KeyframeRef ref)) return false;
            return Objects.equals(entityName, ref.entityName)
                && property == ref.property
                && keyframe == ref.keyframe;
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityName, property, System.identityHashCode(keyframe));
        }
    }

    /** Channel visibility filter. */
    public enum ChannelCategory { TRANSFORM, CAMERA, AUDIO, EVENT }

    private static final Comparator<KeyframeRef> ORDER_BY_TIME = Comparator
        .comparingDouble((KeyframeRef ref) -> ref.keyframe().getTimeMs())
        .thenComparing(KeyframeRef::entityName, Comparator.nullsLast(String::compareTo))
        .thenComparing(ref -> ref.property() != null ? ref.property().ordinal() : -1)
        .thenComparingInt(ref -> System.identityHashCode(ref.keyframe()));

    private final Set<KeyframeRef> selected = new LinkedHashSet<>();
    private boolean rippleRetimeEnabled = false;
    private final Set<ChannelCategory> visibleCategories = new LinkedHashSet<>();

    public KeyframeSelectionModel() {
        Collections.addAll(visibleCategories, ChannelCategory.values());
    }

    public static KeyframeRef ref(String entityName, PropertyType property, Keyframe keyframe) {
        return keyframe != null ? new KeyframeRef(entityName, property, keyframe) : null;
    }

    public static KeyframeRef ref(AnimationProject project, String entityName, PropertyType property, int keyframeIndex) {
        if (project == null || entityName == null || property == null || keyframeIndex < 0) return null;
        EntityTrack track = project.getTrack(entityName);
        if (track == null) return null;
        List<Keyframe> keyframes = track.getKeyframes(property);
        if (keyframeIndex >= keyframes.size()) return null;
        return new KeyframeRef(entityName, property, keyframes.get(keyframeIndex));
    }

    public void select(KeyframeRef ref) {
        if (ref != null && ref.keyframe() != null) selected.add(ref);
    }

    public void deselect(KeyframeRef ref) {
        if (ref != null) selected.remove(ref);
    }

    public void toggleSelect(KeyframeRef ref) {
        if (ref == null || ref.keyframe() == null) return;
        if (selected.contains(ref)) selected.remove(ref);
        else selected.add(ref);
    }

    public void replaceSelection(Iterable<KeyframeRef> refs) {
        selected.clear();
        if (refs == null) return;
        for (KeyframeRef ref : refs) {
            if (ref != null && ref.keyframe() != null) selected.add(ref);
        }
    }

    public void clearSelection() {
        selected.clear();
    }

    public boolean isSelected(KeyframeRef ref) {
        return ref != null && selected.contains(ref);
    }

    public Set<KeyframeRef> getSelected() {
        return Collections.unmodifiableSet(selected);
    }

    public List<KeyframeRef> getSelectedOrdered() {
        List<KeyframeRef> ordered = new ArrayList<>(selected);
        ordered.sort(ORDER_BY_TIME);
        return ordered;
    }

    public int getSelectionCount() {
        return selected.size();
    }

    public boolean hasSelection() {
        return !selected.isEmpty();
    }

    public Set<String> getSelectedEntityNames() {
        Set<String> names = new LinkedHashSet<>();
        for (KeyframeRef ref : selected) {
            if (ref.entityName() != null) names.add(ref.entityName());
        }
        return names;
    }

    public double getMinSelectedTimeMs() {
        return getSelectedOrdered().stream()
            .mapToDouble(ref -> ref.keyframe().getTimeMs())
            .min()
            .orElse(0.0);
    }

    public double getMaxSelectedTimeMs() {
        return getSelectedOrdered().stream()
            .mapToDouble(ref -> ref.keyframe().getTimeMs())
            .max()
            .orElse(0.0);
    }

    public void boxSelect(AnimationProject project, String entityName,
                          double startMs, double endMs, boolean additive) {
        if (project == null) return;
        if (!additive) selected.clear();
        double lo = Math.min(startMs, endMs);
        double hi = Math.max(startMs, endMs);

        for (EntityTrack track : project.getTracks()) {
            if (entityName != null && !track.getEntityName().equals(entityName)) continue;
            for (PropertyType prop : PropertyType.values()) {
                if (!isCategoryVisible(prop)) continue;
                for (Keyframe keyframe : track.getKeyframes(prop)) {
                    double time = keyframe.getTimeMs();
                    if (time >= lo && time <= hi) {
                        selected.add(new KeyframeRef(track.getEntityName(), prop, keyframe));
                    }
                }
            }
        }
    }

    public boolean isRippleRetimeEnabled() { return rippleRetimeEnabled; }
    public void setRippleRetimeEnabled(boolean enabled) { this.rippleRetimeEnabled = enabled; }

    public void moveSelected(AnimationProject project, double deltaMs, double snap) {
        if (project == null || selected.isEmpty() || Math.abs(deltaMs) < 1e-9) return;

        List<KeyframeRef> refs = getSelectedOrdered();
        double latestSelectedTime = refs.stream()
            .mapToDouble(ref -> ref.keyframe().getTimeMs())
            .max()
            .orElse(Double.NEGATIVE_INFINITY);

        for (KeyframeRef ref : refs) {
            double newTime = snap(ref.keyframe().getTimeMs() + deltaMs, snap);
            ref.keyframe().setTimeMs(Math.max(0.0, newTime));
        }

        if (rippleRetimeEnabled && Double.isFinite(latestSelectedTime)) {
            for (EntityTrack track : project.getTracks()) {
                for (PropertyType prop : PropertyType.values()) {
                    for (Keyframe keyframe : track.getKeyframes(prop)) {
                        if (containsKeyframe(track.getEntityName(), prop, keyframe)) continue;
                        if (keyframe.getTimeMs() > latestSelectedTime) {
                            double newTime = snap(keyframe.getTimeMs() + deltaMs, snap);
                            keyframe.setTimeMs(Math.max(0.0, newTime));
                        }
                    }
                    track.sortKeyframes(prop);
                }
            }
            return;
        }

        sortAffectedTracks(project);
    }

    public void stretchSelected(AnimationProject project, double factor, double snap) {
        if (project == null || selected.size() < 2) return;
        double safeFactor = Double.isFinite(factor) ? factor : 1.0;
        safeFactor = Math.max(0.05, safeFactor);

        List<KeyframeRef> refs = getSelectedOrdered();
        double start = refs.get(0).keyframe().getTimeMs();
        for (KeyframeRef ref : refs) {
            double offset = ref.keyframe().getTimeMs() - start;
            double newTime = snap(start + offset * safeFactor, snap);
            ref.keyframe().setTimeMs(Math.max(0.0, newTime));
        }
        sortAffectedTracks(project);
    }

    public void reverseSelected(AnimationProject project, double snap) {
        if (project == null || selected.size() < 2) return;
        double min = getMinSelectedTimeMs();
        double max = getMaxSelectedTimeMs();
        for (KeyframeRef ref : getSelectedOrdered()) {
            double mirrored = min + (max - ref.keyframe().getTimeMs());
            ref.keyframe().setTimeMs(Math.max(0.0, snap(mirrored, snap)));
        }
        sortAffectedTracks(project);
    }

    public void distributeSelected(AnimationProject project, double snap) {
        if (project == null || selected.size() < 3) return;
        List<KeyframeRef> refs = getSelectedOrdered();
        double min = refs.get(0).keyframe().getTimeMs();
        double max = refs.get(refs.size() - 1).keyframe().getTimeMs();
        double span = max - min;
        if (span < 1e-9) return;

        for (int i = 0; i < refs.size(); i++) {
            double t = min + (span * i) / (refs.size() - 1);
            refs.get(i).keyframe().setTimeMs(Math.max(0.0, snap(t, snap)));
        }
        sortAffectedTracks(project);
    }

    public Set<ChannelCategory> getVisibleCategories() {
        return Collections.unmodifiableSet(visibleCategories);
    }

    public void setVisibleCategory(ChannelCategory cat, boolean visible) {
        if (cat == null) return;
        if (visible) visibleCategories.add(cat);
        else visibleCategories.remove(cat);
    }

    public boolean isCategoryVisible(ChannelCategory cat) {
        return cat != null && visibleCategories.contains(cat);
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

    private boolean containsKeyframe(String entityName, PropertyType property, Keyframe keyframe) {
        return selected.contains(new KeyframeRef(entityName, property, keyframe));
    }

    private void sortAffectedTracks(AnimationProject project) {
        Set<String> entities = getSelectedEntityNames();
        for (String entity : entities) {
            EntityTrack track = project.getTrack(entity);
            if (track == null) continue;
            for (PropertyType property : PropertyType.values()) {
                track.sortKeyframes(property);
            }
        }
    }

    private static double snap(double value, double snap) {
        if (!(snap > 0)) return value;
        return Math.round(value / snap) * snap;
    }
}
