package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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
    private final Map<String, double[]> orbitAnchors = new LinkedHashMap<>();
    private final Map<String, String> orbitAnchorSources = new LinkedHashMap<>();
    private final Map<String, double[]> orbitAnchorSourceOffsets = new LinkedHashMap<>();

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

    public Map<String, double[]> getOrbitAnchorsView() {
        Map<String, double[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : orbitAnchors.entrySet()) {
            double[] value = entry.getValue();
            if (value == null || value.length < 2) continue;
            copy.put(entry.getKey(), new double[]{value[0], value[1]});
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, String> getOrbitAnchorSourcesView() {
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : orbitAnchorSources.entrySet()) {
            String target = entry.getKey();
            String source = entry.getValue();
            if (target == null || target.isBlank() || source == null || source.isBlank()) continue;
            copy.put(target, source);
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, double[]> getOrbitAnchorSourceOffsetsView() {
        Map<String, double[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> entry : orbitAnchorSourceOffsets.entrySet()) {
            String target = entry.getKey();
            double[] value = entry.getValue();
            if (target == null || target.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            copy.put(target, new double[]{value[0], value[1]});
        }
        return Collections.unmodifiableMap(copy);
    }

    public boolean hasOrbitAnchor(String entityName) {
        return entityName != null && orbitAnchors.containsKey(entityName);
    }

    public double[] getOrbitAnchor(String entityName) {
        if (entityName == null || entityName.isBlank()) return null;
        double[] value = orbitAnchors.get(entityName);
        if (value == null || value.length < 2) return null;
        return new double[]{value[0], value[1]};
    }

    public void setOrbitAnchor(String entityName, double worldX, double worldY) {
        if (entityName == null || entityName.isBlank()) return;
        if (!Double.isFinite(worldX) || !Double.isFinite(worldY)) return;
        orbitAnchors.put(entityName, new double[]{worldX, worldY});
        orbitAnchorSources.remove(entityName);
    }

    public void setOrbitAnchorSource(String entityName, String sourceEntityName) {
        setOrbitAnchorSource(entityName, sourceEntityName, 0.0, 0.0);
    }

    public void setOrbitAnchorSource(String entityName, String sourceEntityName, double offsetX, double offsetY) {
        if (entityName == null || entityName.isBlank()) return;
        if (sourceEntityName == null || sourceEntityName.isBlank()) {
            orbitAnchorSources.remove(entityName);
            orbitAnchorSourceOffsets.remove(entityName);
            return;
        }
        if (entityName.equals(sourceEntityName)) {
            orbitAnchorSources.remove(entityName);
            orbitAnchorSourceOffsets.remove(entityName);
            return;
        }
        orbitAnchorSources.put(entityName, sourceEntityName);
        setOrbitAnchorSourceOffset(entityName, offsetX, offsetY);
    }

    public void setOrbitAnchorSourceOffset(String entityName, double offsetX, double offsetY) {
        if (entityName == null || entityName.isBlank()) return;
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)) return;
        if (!orbitAnchorSources.containsKey(entityName)) return;
        orbitAnchorSourceOffsets.put(entityName, new double[]{offsetX, offsetY});
    }

    public void clearOrbitAnchorSourceOffset(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchorSourceOffsets.remove(entityName);
    }

    public void removeOrbitAnchor(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchors.remove(entityName);
        orbitAnchorSources.remove(entityName);
        orbitAnchorSourceOffsets.remove(entityName);
    }

    public void clearOrbitAnchors() {
        orbitAnchors.clear();
        orbitAnchorSources.clear();
        orbitAnchorSourceOffsets.clear();
    }

    public void setOrbitAnchors(Map<String, double[]> anchors) {
        orbitAnchors.clear();
        if (anchors == null || anchors.isEmpty()) {
            orbitAnchorSources.clear();
            orbitAnchorSourceOffsets.clear();
            return;
        }
        for (Map.Entry<String, double[]> entry : anchors.entrySet()) {
            String name = entry.getKey();
            double[] value = entry.getValue();
            if (name == null || name.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            orbitAnchors.put(name, new double[]{value[0], value[1]});
        }
        orbitAnchorSources.keySet().removeIf(name -> !orbitAnchors.containsKey(name));
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
    }

    public void setOrbitAnchorSources(Map<String, String> sources) {
        orbitAnchorSources.clear();
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchors.containsKey(name));
        if (sources == null || sources.isEmpty()) return;
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String target = entry.getKey();
            String source = entry.getValue();
            if (target == null || target.isBlank() || source == null || source.isBlank()) continue;
            if (target.equals(source)) continue;
            if (!orbitAnchors.containsKey(target)) continue;
            orbitAnchorSources.put(target, source);
            orbitAnchorSourceOffsets.putIfAbsent(target, new double[]{0.0, 0.0});
        }
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
    }

    public void setOrbitAnchorSourceOffsets(Map<String, double[]> offsets) {
        orbitAnchorSourceOffsets.clear();
        if (offsets == null || offsets.isEmpty()) return;
        for (Map.Entry<String, double[]> entry : offsets.entrySet()) {
            String target = entry.getKey();
            double[] value = entry.getValue();
            if (target == null || target.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            if (!orbitAnchorSources.containsKey(target)) continue;
            orbitAnchorSourceOffsets.put(target, new double[]{value[0], value[1]});
        }
        for (String target : orbitAnchorSources.keySet()) {
            orbitAnchorSourceOffsets.putIfAbsent(target, new double[]{0.0, 0.0});
        }
    }

    public void pruneOrbitAnchors(Set<String> validEntityNames) {
        if (validEntityNames == null || validEntityNames.isEmpty()) {
            orbitAnchors.clear();
            orbitAnchorSources.clear();
            return;
        }
        Set<String> allowed = new HashSet<>();
        for (String name : validEntityNames) {
            if (name != null && !name.isBlank()) allowed.add(name);
        }
        orbitAnchors.keySet().removeIf(name -> !allowed.contains(name));
        orbitAnchorSources.entrySet().removeIf(entry ->
            !allowed.contains(entry.getKey()) || !allowed.contains(entry.getValue()));
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
    }

    private void removeOrbitAnchorReferencesTo(String entityName) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchorSources.entrySet().removeIf(entry ->
            entityName.equals(entry.getKey()) || entityName.equals(entry.getValue()));
        orbitAnchorSourceOffsets.keySet().removeIf(name ->
            entityName.equals(name) || !orbitAnchorSources.containsKey(name));
        orbitAnchors.remove(entityName);
    }

    public void captureInitialSnapshot() {
        Map<String, Map<PropertyType, Double>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, EntityTrack> entry : entityTracks.entrySet()) {
            Map<PropertyType, Double> props = new LinkedHashMap<>();
            for (PropertyType p : PropertyType.values()) {
                props.put(p, entry.getValue().getValueAt(p, 0));
            }
            snapshot.put(entry.getKey(), props);
        }
        setInitialSnapshot(snapshot);
    }

    public Map<String, Map<PropertyType, Double>> getInitialSnapshot() {
        return initialSnapshot;
    }

    public void setInitialSnapshot(Map<String, Map<PropertyType, Double>> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            initialSnapshot = null;
            return;
        }
        Map<String, Map<PropertyType, Double>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<PropertyType, Double>> entry : snapshot.entrySet()) {
            String entityName = entry.getKey();
            if (entityName == null || entityName.isBlank()) continue;
            Map<PropertyType, Double> source = entry.getValue();
            Map<PropertyType, Double> props = new LinkedHashMap<>();
            if (source != null) {
                for (Map.Entry<PropertyType, Double> propEntry : source.entrySet()) {
                    PropertyType property = propEntry.getKey();
                    Double value = propEntry.getValue();
                    if (property == null || value == null || !Double.isFinite(value)) continue;
                    props.put(property, value);
                }
            }
            copy.put(entityName, props);
        }
        initialSnapshot = copy.isEmpty() ? null : copy;
    }

    public Double getInitialSnapshotValue(String entityName, PropertyType property) {
        if (initialSnapshot == null || entityName == null || property == null) return null;
        Map<PropertyType, Double> values = initialSnapshot.get(entityName);
        return values != null ? values.get(property) : null;
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
        removeOrbitAnchorReferencesTo(entityName);
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
            EntityGroup parent = g.hasParent() ? groups.get(g.getParentGroupName()) : null;
            if (g.hasParent()) {
                if (parent != null) parent.removeChildGroup(name);
            }
            for (String child : g.getChildEntityNames()) {
                EntityTrack t = entityTracks.get(child);
                if (t == null) continue;
                if (parent != null) {
                    t.setParentGroupName(parent.getName());
                    parent.addChildEntity(child);
                } else {
                    t.setParentGroupName(null);
                    if (!rootEntityNames.contains(child)) rootEntityNames.add(child);
                }
            }
            for (String childGroup : g.getChildGroupNames()) {
                EntityGroup cg = groups.get(childGroup);
                if (cg == null) continue;
                if (parent != null) {
                    cg.setParentGroupName(parent.getName());
                    parent.addChildGroup(childGroup);
                } else {
                    cg.setParentGroupName(null);
                    if (!rootGroupNames.contains(childGroup)) rootGroupNames.add(childGroup);
                }
            }
        }
    }

    public Iterable<EntityGroup> getGroups() { return groups.values(); }

    public List<String> getRootEntityNames() { return Collections.unmodifiableList(rootEntityNames); }
    public List<String> getRootGroupNames() { return Collections.unmodifiableList(rootGroupNames); }

    public static boolean isGroupProperty(PropertyType property) {
        return property == PropertyType.X
            || property == PropertyType.Y
            || property == PropertyType.Z
            || property == PropertyType.ROTATION
            || property == PropertyType.SCALE_X
            || property == PropertyType.SCALE_Y
            || property == PropertyType.ALPHA;
    }

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

    public boolean canAddGroupToGroup(String childGroupName, String parentGroupName) {
        EntityGroup child = groups.get(childGroupName);
        EntityGroup parent = groups.get(parentGroupName);
        if (child == null || parent == null) return false;
        if (childGroupName.equals(parentGroupName)) return false;
        return !wouldCreateCycle(parentGroupName, childGroupName);
    }

    public void addGroupToGroup(String childGroupName, String parentGroupName) {
        EntityGroup child = groups.get(childGroupName);
        EntityGroup parent = groups.get(parentGroupName);
        if (!canAddGroupToGroup(childGroupName, parentGroupName)) return;

        String oldParent = child.getParentGroupName();
        if (oldParent != null) {
            EntityGroup oldGroup = groups.get(oldParent);
            if (oldGroup != null) oldGroup.removeChildGroup(childGroupName);
        }
        rootGroupNames.remove(childGroupName);

        child.setParentGroupName(parentGroupName);
        parent.addChildGroup(childGroupName);
    }

    public boolean renameGroup(String currentName, String nextName) {
        if (currentName == null || currentName.isBlank() || nextName == null || nextName.isBlank()) return false;
        if (currentName.equals(nextName)) return true;
        EntityGroup source = groups.get(currentName);
        if (source == null || groups.containsKey(nextName)) return false;

        EntityGroup renamed = new EntityGroup(nextName);
        renamed.setParentGroupName(source.getParentGroupName());
        renamed.setExpanded(source.isExpanded());
        renamed.setLayerOrder(source.getLayerOrder());
        for (String childEntity : source.getChildEntityNames()) {
            renamed.addChildEntity(childEntity);
        }
        for (String childGroup : source.getChildGroupNames()) {
            renamed.addChildGroup(childGroup);
        }

        EntityTrack sourceTrack = source.getGroupTrack();
        EntityTrack renamedTrack = renamed.getGroupTrack();
        renamedTrack.setExpanded(sourceTrack.isExpanded());
        renamedTrack.setVisible(sourceTrack.isVisible());
        renamedTrack.setLayerOrder(sourceTrack.getLayerOrder());
        for (PropertyType property : PropertyType.values()) {
            List<Keyframe> sourceKeyframes = sourceTrack.getKeyframes(property);
            if (sourceKeyframes.isEmpty()) continue;
            List<Keyframe> copied = new ArrayList<>();
            for (Keyframe keyframe : sourceKeyframes) {
                copied.add(keyframe.copy());
            }
            renamedTrack.setKeyframes(property, copied);
        }

        LinkedHashMap<String, EntityGroup> updated = new LinkedHashMap<>();
        for (Map.Entry<String, EntityGroup> entry : groups.entrySet()) {
            if (currentName.equals(entry.getKey())) {
                updated.put(nextName, renamed);
            } else {
                updated.put(entry.getKey(), entry.getValue());
            }
        }
        groups.clear();
        groups.putAll(updated);
        replaceName(rootGroupNames, currentName, nextName);

        if (source.hasParent()) {
            EntityGroup parent = groups.get(source.getParentGroupName());
            if (parent != null) {
                parent.replaceChildGroup(currentName, nextName);
            }
        }
        for (String childEntity : source.getChildEntityNames()) {
            EntityTrack track = entityTracks.get(childEntity);
            if (track != null) {
                track.setParentGroupName(nextName);
            }
        }
        for (String childGroup : source.getChildGroupNames()) {
            EntityGroup child = groups.get(childGroup);
            if (child != null) {
                child.setParentGroupName(nextName);
            }
        }
        return true;
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
        return computeValueAt(entityName, property, timeMs, property != null ? property.getDefaultValue() : 0.0);
    }

    public double computeValueAt(String entityName, PropertyType property, double timeMs, double fallbackLocalValue) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return fallbackLocalValue;

        double localValue = track.hasKeyframes(property)
            ? track.getValueAt(property, timeMs)
            : fallbackLocalValue;

        if (track.hasParent() && property.isEntityProperty() && isAdditiveGroupProperty(property)) {
            double parentValue = computeGroupValueAt(track.getParentGroupName(), property, timeMs);
            return localValue + parentValue;
        }
        if (track.hasParent() && property.isEntityProperty() && isMultiplicativeGroupProperty(property)) {
            double parentValue = computeGroupValueAt(track.getParentGroupName(), property, timeMs);
            return localValue * parentValue;
        }
        return localValue;
    }

    public boolean hasEffectiveAnimation(String entityName, PropertyType property) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null) return false;
        if (track.hasKeyframes(property)) return true;
        return track.hasParent()
            && property.isEntityProperty()
            && hasGroupAnimation(track.getParentGroupName(), property);
    }

    public List<Keyframe> getEffectiveKeyframes(String entityName, PropertyType property) {
        if (entityName == null || entityName.isBlank() || property == null) return Collections.emptyList();
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return Collections.emptyList();
        if (!property.isEntityProperty()) return track.getKeyframes(property);
        if (!track.hasParent() || (!track.hasKeyframes(property) && !hasGroupAnimation(track.getParentGroupName(), property))) {
            return track.getKeyframes(property);
        }

        Map<Long, Double> times = new TreeMap<>();
        collectEffectiveTimes(entityName, property, times);
        if (times.isEmpty()) return Collections.emptyList();

        List<Keyframe> out = new ArrayList<>();
        for (double time : times.values()) {
            Keyframe source = resolveEffectiveStyleSource(entityName, property, time);
            out.add(new Keyframe(
                time,
                computeValueAt(entityName, property, time, property.getDefaultValue()),
                source != null ? source.getEasingSpec() : com.jvn.core.animation.EasingSpec.of(com.jvn.core.animation.Easing.Type.LINEAR),
                source != null ? source.getInterpolation() : com.jvn.core.animation.Easing.Interpolation.TWEEN
            ));
        }
        return out;
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
        double value = isMultiplicativeGroupProperty(property) ? 1.0 : 0.0;
        java.util.Set<String> visited = new java.util.HashSet<>();
        String cursor = groupName;
        while (cursor != null) {
            if (!visited.add(cursor)) break;
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            EntityTrack groupTrack = group.getGroupTrack();
            if (groupTrack.hasKeyframes(property)) {
                double current = groupTrack.getValueAt(property, timeMs);
                if (isMultiplicativeGroupProperty(property)) {
                    value *= current;
                } else {
                    value += current;
                }
            }
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
        copy.setOrbitAnchors(getOrbitAnchorsView());
        copy.setOrbitAnchorSources(getOrbitAnchorSourcesView());
        copy.setOrbitAnchorSourceOffsets(getOrbitAnchorSourceOffsetsView());
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
        copy.setInitialSnapshot(initialSnapshot);
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
                List<Keyframe> keyframes = prop.isEntityProperty()
                    ? getEffectiveKeyframes(et.getEntityName(), prop)
                    : et.getKeyframes(prop);
                if (keyframes.isEmpty()) continue;
                hasData = true;

                for (Keyframe kf : keyframes) {
                    TimelineData.Keyframe runtimeKeyframe = new TimelineData.Keyframe(
                        kf.getTimeMs(),
                        kf.getValue(),
                        kf.getEasingSpec(),
                        kf.getInterpolation()
                    );
                    TimelineData.Property runtimeProp = mapProperty(prop);
                    if (runtimeProp != null) {
                        track.addKeyframe(runtimeProp, runtimeKeyframe);
                    } else if (prop.isTimelineCustomProperty()) {
                        track.addCustomKeyframe(prop.getTimelineCustomKey(), runtimeKeyframe);
                    }
                }
            }

            for (String customPropertyKey : et.getAnimatedCustomProperties()) {
                if (customPropertyKey == null || customPropertyKey.isBlank()) continue;
                List<Keyframe> keyframes = et.getCustomKeyframes(customPropertyKey);
                if (keyframes.isEmpty()) continue;
                hasData = true;
                for (Keyframe kf : keyframes) {
                    track.addCustomKeyframe(customPropertyKey,
                        new TimelineData.Keyframe(
                            kf.getTimeMs(),
                            kf.getValue(),
                            kf.getEasingSpec(),
                            kf.getInterpolation()
                        ));
                }
            }

            int layerOrder = computeEffectiveLayerOrder(et.getEntityName());
            if (layerOrder != 0 && !track.hasKeyframes(TimelineData.Property.Z)) {
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
        this.setOrbitAnchors(other.getOrbitAnchorsView());
        this.setOrbitAnchorSources(other.getOrbitAnchorSourcesView());
        this.setOrbitAnchorSourceOffsets(other.getOrbitAnchorSourceOffsetsView());
        this.setInitialSnapshot(other.initialSnapshot);
    }

    private boolean hasGroupAnimation(String groupName, PropertyType property) {
        if (!isGroupProperty(property)) return false;
        Set<String> visited = new HashSet<>();
        String cursor = groupName;
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            if (group.getGroupTrack().hasKeyframes(property)) {
                return true;
            }
            cursor = group.getParentGroupName();
        }
        return false;
    }

    private void collectEffectiveTimes(String entityName, PropertyType property, Map<Long, Double> out) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null || property == null || out == null) return;
        collectTimes(track.getKeyframes(property), out);
        String cursor = track.getParentGroupName();
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            collectTimes(group.getGroupTrack().getKeyframes(property), out);
            cursor = group.getParentGroupName();
        }
    }

    private Keyframe resolveEffectiveStyleSource(String entityName, PropertyType property, double timeMs) {
        EntityTrack track = entityTracks.get(entityName);
        if (track == null) return null;
        Keyframe local = track.findKeyframeAt(property, timeMs);
        if (local != null) return local;
        String cursor = track.getParentGroupName();
        Set<String> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            EntityGroup group = groups.get(cursor);
            if (group == null) break;
            Keyframe source = group.getGroupTrack().findKeyframeAt(property, timeMs);
            if (source != null) return source;
            cursor = group.getParentGroupName();
        }
        return null;
    }

    private void collectTimes(List<Keyframe> keyframes, Map<Long, Double> out) {
        if (keyframes == null || out == null) return;
        for (Keyframe keyframe : keyframes) {
            if (keyframe == null) continue;
            long quantized = Math.round(keyframe.getTimeMs() * 1000.0);
            out.putIfAbsent(quantized, keyframe.getTimeMs());
        }
    }

    private static boolean isAdditiveGroupProperty(PropertyType property) {
        return property == PropertyType.X
            || property == PropertyType.Y
            || property == PropertyType.Z
            || property == PropertyType.ROTATION;
    }

    private static boolean isMultiplicativeGroupProperty(PropertyType property) {
        return property == PropertyType.SCALE_X
            || property == PropertyType.SCALE_Y
            || property == PropertyType.ALPHA;
    }

    private static void replaceName(List<String> values, String currentName, String nextName) {
        if (values == null || currentName == null || nextName == null || currentName.equals(nextName)) return;
        int index = values.indexOf(currentName);
        if (index < 0) return;
        if (values.contains(nextName)) {
            values.remove(index);
            return;
        }
        values.set(index, nextName);
    }

    private static TimelineData.Property mapProperty(PropertyType p) {
        return switch (p) {
            case X -> TimelineData.Property.X;
            case Y -> TimelineData.Property.Y;
            case Z -> TimelineData.Property.Z;
            case PIVOT_X -> TimelineData.Property.PIVOT_X;
            case PIVOT_Y -> TimelineData.Property.PIVOT_Y;
            case ROTATION -> TimelineData.Property.ROTATION;
            case SCALE_X -> TimelineData.Property.SCALE_X;
            case SCALE_Y -> TimelineData.Property.SCALE_Y;
            case ALPHA -> TimelineData.Property.ALPHA;
            case VISIBILITY -> TimelineData.Property.VISIBILITY;
            case CAMERA_X -> TimelineData.Property.CAMERA_X;
            case CAMERA_Y -> TimelineData.Property.CAMERA_Y;
            case CAMERA_ZOOM -> TimelineData.Property.CAMERA_ZOOM;
            case MATRIX_MXX, MATRIX_MXY, MATRIX_MYX, MATRIX_MYY, MATRIX_TX, MATRIX_TY,
                BLUR, CAMERA_DOF_FOCUS, CAMERA_DOF_STRENGTH, CAMERA_DOF_MAX_BLUR -> null;
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
