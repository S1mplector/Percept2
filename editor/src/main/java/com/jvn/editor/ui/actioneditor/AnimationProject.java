package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnimationProject {
    private String name = "Untitled Animation";
    private double totalDurationMs = 3000;
    private double playheadMs = 0;
    private boolean playing = false;
    private boolean looping = false;

    private final Map<String, EntityTrack> entityTracks = new LinkedHashMap<>();
    private final Map<String, EntityGroup> groups = new LinkedHashMap<>();
    private final List<String> rootEntityNames = new ArrayList<>();
    private final List<String> rootGroupNames = new ArrayList<>();

    public AnimationProject() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : "Untitled Animation"; }

    public double getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(double totalDurationMs) { 
        this.totalDurationMs = Math.max(100, totalDurationMs); 
    }

    public double getPlayheadMs() { return playheadMs; }
    public void setPlayheadMs(double playheadMs) { 
        this.playheadMs = Math.max(0, Math.min(totalDurationMs, playheadMs)); 
    }

    public boolean isPlaying() { return playing; }
    public void setPlaying(boolean playing) { this.playing = playing; }

    public boolean isLooping() { return looping; }
    public void setLooping(boolean looping) { this.looping = looping; }

    public EntityTrack getTrack(String entityName) { return entityTracks.get(entityName); }

    public EntityTrack getOrCreateTrack(String entityName) {
        return entityTracks.computeIfAbsent(entityName, EntityTrack::new);
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
        entityTracks.remove(entityName);
        rootEntityNames.remove(entityName);
    }

    public Iterable<EntityTrack> getTracks() { return entityTracks.values(); }
    public int getTrackCount() { return entityTracks.size(); }

    public EntityGroup getGroup(String name) { return groups.get(name); }

    public EntityGroup getOrCreateGroup(String name) {
        return groups.computeIfAbsent(name, EntityGroup::new);
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
            for (String child : g.getChildEntityNames()) {
                EntityTrack t = entityTracks.get(child);
                if (t != null) t.setParentGroupName(null);
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

    public void addGroupToGroup(String childGroupName, String parentGroupName) {
        EntityGroup child = groups.get(childGroupName);
        EntityGroup parent = groups.get(parentGroupName);
        if (child == null || parent == null || childGroupName.equals(parentGroupName)) return;

        String oldParent = child.getParentGroupName();
        if (oldParent != null) {
            EntityGroup oldGroup = groups.get(oldParent);
            if (oldGroup != null) oldGroup.removeChildGroup(childGroupName);
        }
        rootGroupNames.remove(childGroupName);

        child.setParentGroupName(parentGroupName);
        parent.addChildGroup(childGroupName);
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

    private double computeGroupValueAt(String groupName, PropertyType property, double timeMs) {
        EntityGroup group = groups.get(groupName);
        if (group == null) return 0;

        double value = group.getGroupTrack().getValueAt(property, timeMs);

        if (group.hasParent()) {
            value += computeGroupValueAt(group.getParentGroupName(), property, timeMs);
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
        for (EntityTrack t : entityTracks.values()) {
            copy.entityTracks.put(t.getEntityName(), t.copy());
        }
        for (EntityGroup g : groups.values()) {
            copy.groups.put(g.getName(), g.copy());
        }
        copy.rootEntityNames.addAll(rootEntityNames);
        copy.rootGroupNames.addAll(rootGroupNames);
        return copy;
    }
}
