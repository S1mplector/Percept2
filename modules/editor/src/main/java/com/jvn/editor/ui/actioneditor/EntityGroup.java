package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntityGroup {
    private final String name;
    private String parentGroupName;
    private final List<String> childEntityNames = new ArrayList<>();
    private final List<String> childGroupNames = new ArrayList<>();
    private final EntityTrack groupTrack;
    private boolean expanded = true;
    private boolean locked = false;
    private int layerOrder = 0;

    public EntityGroup(String name) {
        this.name = name;
        this.groupTrack = new EntityTrack(name);
    }

    public String getName() { return name; }

    public String getParentGroupName() { return parentGroupName; }
    public void setParentGroupName(String parentGroupName) { this.parentGroupName = parentGroupName; }
    public boolean hasParent() { return parentGroupName != null && !parentGroupName.isBlank(); }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public int getLayerOrder() { return layerOrder; }
    public void setLayerOrder(int layerOrder) { this.layerOrder = layerOrder; }

    public EntityTrack getGroupTrack() { return groupTrack; }

    public List<String> getChildEntityNames() { return Collections.unmodifiableList(childEntityNames); }
    public void addChildEntity(String entityName) {
        if (entityName != null && !entityName.isBlank() && !childEntityNames.contains(entityName)) {
            childEntityNames.add(entityName);
        }
    }
    public void removeChildEntity(String entityName) { childEntityNames.remove(entityName); }

    public List<String> getChildGroupNames() { return Collections.unmodifiableList(childGroupNames); }
    public void addChildGroup(String groupName) {
        if (groupName != null && !groupName.isBlank() && !childGroupNames.contains(groupName)) {
            childGroupNames.add(groupName);
        }
    }
    public void removeChildGroup(String groupName) { childGroupNames.remove(groupName); }
    public void replaceChildGroup(String currentName, String nextName) {
        if (currentName == null || nextName == null || currentName.equals(nextName)) return;
        int index = childGroupNames.indexOf(currentName);
        if (index < 0) return;
        if (childGroupNames.contains(nextName)) {
            childGroupNames.remove(index);
            return;
        }
        childGroupNames.set(index, nextName);
    }

    public boolean hasChildren() {
        return !childEntityNames.isEmpty() || !childGroupNames.isEmpty();
    }

    public double getMaxTimeMs() {
        return groupTrack.getMaxTimeMs();
    }

    public EntityGroup copy() {
        EntityGroup copy = new EntityGroup(name);
        copy.parentGroupName = parentGroupName;
        copy.expanded = expanded;
        copy.layerOrder = layerOrder;
        copy.childEntityNames.addAll(childEntityNames);
        copy.childGroupNames.addAll(childGroupNames);
        for (PropertyType p : PropertyType.values()) {
            List<Keyframe> src = groupTrack.getKeyframes(p);
            if (!src.isEmpty()) {
                List<Keyframe> dst = new ArrayList<>();
                for (Keyframe kf : src) dst.add(kf.copy());
                copy.groupTrack.setKeyframes(p, dst);
            }
        }
        return copy;
    }
}
