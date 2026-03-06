package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.animation.TimelineData;

public class AnimationProject {
    private static final double MIN_DURATION_MS = 100.0;

    private String name = "Untitled Animation";
    private double totalDurationMs = 3000;
    private double playheadMs = 0;
    private boolean playing = false;
    private boolean looping = false;

    private final Map<String, EntityTrack> entityTracks = new LinkedHashMap<>();
    private final Map<String, EntityGroup> groups = new LinkedHashMap<>();
    private final List<String> rootEntityNames = new ArrayList<>();
    private final List<String> rootGroupNames = new ArrayList<>();

    private final List<AudioCue> audioCues = new ArrayList<>();
    private final List<EditorEventCue> editorEventCues = new ArrayList<>();

    private double loopStartMs = -1;
    private double loopEndMs = -1;

    private Map<String, Map<PropertyType, Double>> initialSnapshot;

    public AnimationProject() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : "Untitled Animation"; }

    public double getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(double totalDurationMs) { 
        this.totalDurationMs = sanitizeDuration(totalDurationMs);
        this.playheadMs = clampTime(playheadMs, this.totalDurationMs);
        normalizeLoopRegion();
    }

    public double getPlayheadMs() { return playheadMs; }
    public void setPlayheadMs(double playheadMs) { 
        this.playheadMs = clampTime(playheadMs, totalDurationMs);
    }

    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean playing) { this.playing = playing; }

    public boolean isLooping() { return looping; }
    public void setLooping(boolean looping) { this.looping = looping; }

    public double getLoopStartMs() { return loopStartMs; }
    public double getLoopEndMs() { return loopEndMs; }
    public boolean hasLoopRegion() { return loopStartMs >= 0 && loopEndMs > loopStartMs; }
    public void setLoopRegion(double startMs, double endMs) {
        this.loopStartMs = sanitizeNonNegativeFinite(startMs, 0.0);
        this.loopEndMs = sanitizeNonNegativeFinite(endMs, this.loopStartMs + 1.0);
        normalizeLoopRegion();
    }
    public void clearLoopRegion() {
        this.loopStartMs = -1;
        this.loopEndMs = -1;
    }

    public List<AudioCue> getAudioCues() { return Collections.unmodifiableList(audioCues); }
    public void addAudioCue(AudioCue cue) {
        if (cue != null) {
            audioCues.add(cue);
            audioCues.sort(AudioCue::compareTo);
        }
    }
    public void removeAudioCue(AudioCue cue) { audioCues.remove(cue); }
    public void clearAudioCues() { audioCues.clear(); }

    public List<EditorEventCue> getEditorEventCues() { return Collections.unmodifiableList(editorEventCues); }
    public void addEditorEventCue(EditorEventCue cue) {
        if (cue != null) {
            editorEventCues.add(cue);
            editorEventCues.sort(EditorEventCue::compareTo);
        }
    }
    public void removeEditorEventCue(EditorEventCue cue) { editorEventCues.remove(cue); }
    public void clearEditorEventCues() { editorEventCues.clear(); }

    public void captureInitialSnapshot() {
        initialSnapshot = new LinkedHashMap<>();
        for (Map.Entry<String, EntityTrack> entry : entityTracks.entrySet()) {
            Map<PropertyType, Double> props = new LinkedHashMap<>();
            for (PropertyType p : PropertyType.values()) {
                props.put(p, entry.getValue().getValueAt(p, 0));
            }
            initialSnapshot.put(entry.getKey(), props);
        }
    }

    public Map<String, Map<PropertyType, Double>> getInitialSnapshot() {
        return initialSnapshot;
    }

    public EntityTrack getTrack(String entityName) { return entityTracks.get(entityName); }

    public EntityTrack getOrCreateTrack(String entityName) {
        EntityTrack existing = entityTracks.get(entityName);
        if (existing != null) return existing;
        EntityTrack track = new EntityTrack(entityName);
        entityTracks.put(entityName, track);
        if (!rootEntityNames.contains(entityName)) {
            rootEntityNames.add(entityName);
        }
        return track;
    }

    public void addTrack(EntityTrack track) {
        if (track != null) {
            entityTracks.put(track.getEntityName(), track);
            if (!track.hasParent() && !rootEntityNames.contains(track.getEntityName())) {
                rootEntityNames.add(track.getEntityName());
            }
        }
    }

    public void removeTrack(String entityName) {
        EntityTrack track = entityTracks.remove(entityName);
        rootEntityNames.remove(entityName);
        if (track != null && track.hasParent()) {
            EntityGroup g = groups.get(track.getParentGroupName());
            if (g != null) g.removeChildEntity(entityName);
        }
    }

    public Iterable<EntityTrack> getTracks() { return entityTracks.values(); }
    public int getTrackCount() { return entityTracks.size(); }

    public EntityGroup getGroup(String name) { return groups.get(name); }

    public EntityGroup getOrCreateGroup(String name) {
        EntityGroup existing = groups.get(name);
        if (existing != null) return existing;

        EntityGroup group = new EntityGroup(name);
        groups.put(name, group);
        if (!group.hasParent() && !rootGroupNames.contains(group.getName())) {
            rootGroupNames.add(group.getName());
        }
        return group;
    }

    public void addGroup(EntityGroup group) {
        if (group != null) {
            groups.put(group.getName(), group);
            if (!group.hasParent() && !rootGroupNames.contains(group.getName())) {
                rootGroupNames.add(group.getName());
            }
        }
    }

    public void removeGroup(String name) {
        EntityGroup g = groups.remove(name);
        rootGroupNames.remove(name);
        if (g != null) {
            if (g.hasParent()) {
                EntityGroup parent = groups.get(g.getParentGroupName());
                if (parent != null) parent.removeChildGroup(name);
            }
            for (String child : g.getChildEntityNames()) {
                EntityTrack t = entityTracks.get(child);
                if (t != null) {
                    t.setParentGroupName(null);
                    if (!rootEntityNames.contains(child)) rootEntityNames.add(child);
                }
            }
            for (String childGroup : g.getChildGroupNames()) {
                EntityGroup cg = groups.get(childGroup);
                if (cg != null) {
                    cg.setParentGroupName(null);
                    if (!rootGroupNames.contains(childGroup)) rootGroupNames.add(childGroup);
                }
            }
        }
    }

    public Iterable<EntityGroup> getGroups() { return groups.values(); }

    public List<String> getRootEntityNames() { return Collections.unmodifiableList(rootEntityNames); }
    public List<String> getRootGroupNames() { return Collections.unmodifiableList(rootGroupNames); }

    public void addEntityToGroup(String entityName, String groupName) {
        EntityTrack track = entityTracks.get(entityName);
        EntityGroup group = groups.get(groupName);
        if (track == null || group == null) return;

        String oldParent = track.getParentGroupName();
        if (oldParent != null) {
            EntityGroup oldGroup = groups.get(oldParent);
            if (oldGroup != null) oldGroup.removeChildEntity(entityName);
        }
        rootEntityNames.remove(entityName);

        track.setParentGroupName(groupName);
        group.addChildEntity(entityName);
    }

    public void removeEntityFromGroup(String entityName) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || !track.hasParent()) return;

        EntityGroup group = groups.get(track.getParentGroupName());
        if (group != null) group.removeChildEntity(entityName);
        track.setParentGroupName(null);
        if (!rootEntityNames.contains(entityName)) rootEntityNames.add(entityName);
    }

    public void removeGroupFromParent(String groupName) {
        EntityGroup group = groups.get(groupName);
        if (group == null || !group.hasParent()) return;

        EntityGroup parent = groups.get(group.getParentGroupName());
        if (parent != null) {
            parent.removeChildGroup(groupName);
        }
        group.setParentGroupName(null);
        if (!rootGroupNames.contains(groupName)) {
            rootGroupNames.add(groupName);
        }
    }

    public void addGroupToGroup(String childGroupName, String parentGroupName) {
        EntityGroup child = groups.get(childGroupName);
        EntityGroup parent = groups.get(parentGroupName);
        if (child == null || parent == null || childGroupName.equals(parentGroupName)) return;

        if (wouldCreateCycle(parentGroupName, childGroupName)) return;

        String oldParent = child.getParentGroupName();
        if (oldParent != null) {
            EntityGroup oldGroup = groups.get(oldParent);
            if (oldGroup != null) oldGroup.removeChildGroup(childGroupName);
        }
        rootGroupNames.remove(childGroupName);

        child.setParentGroupName(parentGroupName);
        parent.addChildGroup(childGroupName);
    }

    private boolean wouldCreateCycle(String current, String proposedAncestor) {
        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = current;
        while (cursor != null) {
            if (!visited.add(cursor)) return true;
            if (cursor.equals(proposedAncestor)) return true;
            EntityGroup g = groups.get(cursor);
            cursor = (g != null) ? g.getParentGroupName() : null;
        }
        return false;
    }

    public double computeValueAt(String entityName, PropertyType property, double timeMs) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return property.getDefaultValue();

        double localValue = track.getValueAt(property, timeMs);

        if (track.hasParent() && property.isEntityProperty() && 
            (property == PropertyType.X || property == PropertyType.Y)) {
            double parentValue = computeGroupValueAt(track.getParentGroupName(), property, timeMs);
            return localValue + parentValue;
        }
        return localValue;
    }

    public int computeEffectiveLayerOrder(String entityName) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return 0;

        int local = track.getLayerOrder();
        if (!track.hasParent()) return local;
        return local + computeGroupLayerOrder(track.getParentGroupName());
    }

    private int computeGroupLayerOrder(String groupName) {
        int total = 0;
        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = groupName;
        while (cursor != null) {
            if (!visited.add(cursor)) break;
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            total += group.getLayerOrder();
            cursor = group.getParentGroupName();
        }
        return total;
    }

    private double computeGroupValueAt(String groupName, PropertyType property, double timeMs) {
        double value = 0;
        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = groupName;
        while (cursor != null) {
            if (!visited.add(cursor)) break;
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            value += group.getGroupTrack().getValueAt(property, timeMs);
            cursor = group.getParentGroupName();
        }
        return value;
    }

    public double computeMaxTimeMs() {
        double max = 0;
        for (EntityTrack t : entityTracks.values()) {
            double tm = t.getMaxTimeMs();
            if (tm > max) max = tm;
        }
        for (EntityGroup g : groups.values()) {
            double tm = g.getMaxTimeMs();
            if (tm > max) max = tm;
        }
        return Math.max(max, 100);
    }

    public void fitDurationToContent() {
        setTotalDurationMs(computeMaxTimeMs() + 500);
    }

    public AnimationProject copy() {
        AnimationProject copy = new AnimationProject();
        copy.name = name;
        copy.totalDurationMs = totalDurationMs;
        copy.playheadMs = playheadMs;
        copy.looping = looping;
        copy.loopStartMs = loopStartMs;
        copy.loopEndMs = loopEndMs;
        for (EntityTrack t : entityTracks.values()) {
            copy.entityTracks.put(t.getEntityName(), t.copy());
        }
        for (EntityGroup g : groups.values()) {
            copy.groups.put(g.getName(), g.copy());
        }
        for (AudioCue cue : audioCues) {
            copy.audioCues.add(cue.copy());
        }
        for (EditorEventCue evt : editorEventCues) {
            copy.editorEventCues.add(evt.copy());
        }
        copy.rootEntityNames.addAll(rootEntityNames);
        copy.rootGroupNames.addAll(rootGroupNames);
        return copy;
    }

    /**
     * Convert this editor-side AnimationProject to a lightweight runtime
     * {@link TimelineData} suitable for registration in
     * {@link com.jvn.core.animation.TimelineRegistry}.
     */
    public TimelineData toTimelineData(String timelineName) {
        TimelineData data = new TimelineData(
            timelineName != null ? timelineName : name,
            totalDurationMs
        );
        data.setLooping(looping);

        for (EntityTrack et : entityTracks.values()) {
            TimelineData.Track track = new TimelineData.Track(et.getEntityName());
            boolean hasData = false;

            for (PropertyType prop : PropertyType.values()) {
                List<Keyframe> keyframes = et.getKeyframes(prop);
                if (keyframes.isEmpty()) continue;
                hasData = true;

                TimelineData.Property runtimeProp = mapProperty(prop);
                if (runtimeProp == null) continue;

                for (Keyframe kf : keyframes) {
                    track.addKeyframe(runtimeProp,
                        new TimelineData.Keyframe(kf.getTimeMs(), kf.getValue(), kf.getEasing()));
                }
            }

            int layerOrder = computeEffectiveLayerOrder(et.getEntityName());
            if (layerOrder != 0) {
                hasData = true;
                track.addKeyframe(TimelineData.Property.Z,
                    new TimelineData.Keyframe(0, layerOrder, com.jvn.core.animation.Easing.Type.LINEAR));
            }

            if (hasData) data.addTrack(track);
        }
        for (AudioCue cue : audioCues) {
            if (cue == null || cue.getAudioFile() == null || cue.getAudioFile().isBlank()) continue;
            data.addAudioCue(new TimelineData.AudioCue(
                cue.getTimeMs(),
                cue.getAudioFile(),
                cue.getChannel(),
                cue.getVolume(),
                "music".equalsIgnoreCase(cue.getChannel()),
                cue.isFadeIn() ? cue.getFadeDurationMs() : 0.0
            ));
        }
        for (EditorEventCue evt : editorEventCues) {
            if (evt == null || evt.getType().isBlank()) continue;
            data.addEventCue(new TimelineData.EventCue(
                evt.getTimeMs(), evt.getType(), evt.getPayloadView()));
        }
        return data;
    }

    /**
     * Replaces all tracks, cues, and settings in this project with data from another.
     * The scene graph (entities) is not affected — only the animation model is swapped.
     */
    public void replaceFrom(AnimationProject other) {
        if (other == null) return;
        this.name = other.name;
        setTotalDurationMs(other.totalDurationMs);
        setPlayheadMs(other.playheadMs);
        this.looping = other.looping;
        if (other.hasLoopRegion()) {
            setLoopRegion(other.loopStartMs, other.loopEndMs);
        } else {
            clearLoopRegion();
        }

        this.entityTracks.clear();
        this.rootEntityNames.clear();
        for (EntityTrack t : other.entityTracks.values()) {
            this.entityTracks.put(t.getEntityName(), t.copy());
            if (!t.hasParent()) this.rootEntityNames.add(t.getEntityName());
        }

        this.groups.clear();
        this.rootGroupNames.clear();
        for (EntityGroup g : other.groups.values()) {
            EntityGroup copy = g.copy();
            this.groups.put(copy.getName(), copy);
            if (!g.hasParent()) this.rootGroupNames.add(g.getName());
        }

        this.audioCues.clear();
        for (AudioCue cue : other.audioCues) {
            this.audioCues.add(cue.copy());
        }
        this.editorEventCues.clear();
        for (EditorEventCue evt : other.editorEventCues) {
            this.editorEventCues.add(evt.copy());
        }
    }

    private static TimelineData.Property mapProperty(PropertyType p) {
        return switch (p) {
            case X -> TimelineData.Property.X;
            case Y -> TimelineData.Property.Y;
            case PIVOT_X -> TimelineData.Property.PIVOT_X;
            case PIVOT_Y -> TimelineData.Property.PIVOT_Y;
            case ROTATION -> TimelineData.Property.ROTATION;
            case SCALE_X -> TimelineData.Property.SCALE_X;
            case SCALE_Y -> TimelineData.Property.SCALE_Y;
            case ALPHA -> TimelineData.Property.ALPHA;
            case CAMERA_X -> TimelineData.Property.CAMERA_X;
            case CAMERA_Y -> TimelineData.Property.CAMERA_Y;
            case CAMERA_ZOOM -> TimelineData.Property.CAMERA_ZOOM;
        };
    }

    private void normalizeLoopRegion() {
        if (!Double.isFinite(loopStartMs) || !Double.isFinite(loopEndMs)) {
            clearLoopRegion();
            return;
        }
        if (loopStartMs < 0 || loopEndMs <= loopStartMs) {
            clearLoopRegion();
            return;
        }

        double maxStart = Math.max(0.0, totalDurationMs - 1.0);
        double start = Math.min(loopStartMs, maxStart);
        double end = Math.max(start + 1.0, loopEndMs);
        end = Math.min(end, totalDurationMs);

        if (end <= start) {
            clearLoopRegion();
            return;
        }
        loopStartMs = start;
        loopEndMs = end;
    }

    private static double sanitizeDuration(double value) {
        if (!Double.isFinite(value)) return MIN_DURATION_MS;
        return Math.max(MIN_DURATION_MS, value);
    }

    private static double sanitizeNonNegativeFinite(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, value);
    }

    private static double clampTime(double value, double durationMs) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(durationMs, value));
    }
}
