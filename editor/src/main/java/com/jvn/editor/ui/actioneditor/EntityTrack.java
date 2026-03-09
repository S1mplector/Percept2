package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class EntityTrack {
    private static final double KEYFRAME_TIME_EPSILON_MS = 0.001;

    private final String entityName;
    private String parentGroupName;
    private final Map<PropertyType, List<Keyframe>> keyframes;
    private boolean expanded = true;
    private boolean visible = true;
    private int layerOrder = 0;

    public EntityTrack(String entityName) {
        this.entityName = entityName;
        this.keyframes = new EnumMap<>(PropertyType.class);
    }

    public String getEntityName() { return entityName; }

    public String getParentGroupName() { return parentGroupName; }
    public void setParentGroupName(String parentGroupName) { this.parentGroupName = parentGroupName; }
    public boolean hasParent() { return parentGroupName != null && !parentGroupName.isBlank(); }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public int getLayerOrder() { return layerOrder; }
    public void setLayerOrder(int layerOrder) { this.layerOrder = layerOrder; }

    public List<Keyframe> getKeyframes(PropertyType property) {
        return keyframes.getOrDefault(property, Collections.emptyList());
    }

    public void addKeyframe(PropertyType property, Keyframe kf) {
        upsertKeyframe(property, kf);
    }

    public Keyframe upsertKeyframe(PropertyType property, Keyframe kf) {
        if (property == null || kf == null) return null;
        List<Keyframe> list = keyframes.computeIfAbsent(property, k -> new ArrayList<>());
        for (Keyframe existing : list) {
            if (Math.abs(existing.getTimeMs() - kf.getTimeMs()) <= KEYFRAME_TIME_EPSILON_MS) {
                // Keep easing shape on existing keyframe; only replace value/time.
                existing.setTimeMs(kf.getTimeMs());
                existing.setValue(kf.getValue());
                sortKeyframes(property);
                return existing;
            }
        }
        list.add(kf);
        sortKeyframes(property);
        return kf;
    }

    public void removeKeyframe(PropertyType property, Keyframe kf) {
        List<Keyframe> list = keyframes.get(property);
        if (list != null) {
            list.remove(kf);
            if (list.isEmpty()) keyframes.remove(property);
        }
    }

    public void setKeyframes(PropertyType property, List<Keyframe> kfs) {
        if (kfs == null || kfs.isEmpty()) {
            keyframes.remove(property);
        } else {
            keyframes.put(property, new ArrayList<>(kfs));
            sortKeyframes(property);
        }
    }

    public void sortKeyframes(PropertyType property) {
        List<Keyframe> list = keyframes.get(property);
        if (list != null) Collections.sort(list);
    }

    public Keyframe findKeyframeAt(PropertyType property, double timeMs) {
        List<Keyframe> list = keyframes.get(property);
        if (list == null) return null;
        for (Keyframe kf : list) {
            if (Math.abs(kf.getTimeMs() - timeMs) <= KEYFRAME_TIME_EPSILON_MS) return kf;
        }
        return null;
    }

    public boolean hasKeyframes(PropertyType property) {
        List<Keyframe> list = keyframes.get(property);
        return list != null && !list.isEmpty();
    }

    public Iterable<PropertyType> getAnimatedProperties() {
        return keyframes.keySet();
    }

    public double getValueAt(PropertyType property, double timeMs) {
        List<Keyframe> list = keyframes.get(property);
        if (list == null || list.isEmpty()) return property.getDefaultValue();

        if (timeMs <= list.get(0).getTimeMs()) return list.get(0).getValue();
        if (timeMs >= list.get(list.size() - 1).getTimeMs()) return list.get(list.size() - 1).getValue();

        for (int i = 0; i < list.size() - 1; i++) {
            Keyframe k0 = list.get(i);
            Keyframe k1 = list.get(i + 1);
            if (timeMs >= k0.getTimeMs() && timeMs <= k1.getTimeMs()) {
                double span = k1.getTimeMs() - k0.getTimeMs();
                if (span < 0.001) return k1.getValue();
                double t = (timeMs - k0.getTimeMs()) / span;
                double easedT = com.jvn.core.animation.Easing.applyInterpolation(
                    k1.getEasingSpec(), k1.getInterpolation(), t);
                return k0.getValue() + (k1.getValue() - k0.getValue()) * easedT;
            }
        }
        return property.getDefaultValue();
    }

    public double getMaxTimeMs() {
        double max = 0;
        for (List<Keyframe> list : keyframes.values()) {
            for (Keyframe kf : list) {
                if (kf.getTimeMs() > max) max = kf.getTimeMs();
            }
        }
        return max;
    }

    public EntityTrack copy() {
        EntityTrack copy = new EntityTrack(entityName);
        copy.parentGroupName = parentGroupName;
        copy.expanded = expanded;
        copy.visible = visible;
        copy.layerOrder = layerOrder;
        for (Map.Entry<PropertyType, List<Keyframe>> entry : keyframes.entrySet()) {
            List<Keyframe> copyList = new ArrayList<>();
            for (Keyframe kf : entry.getValue()) copyList.add(kf.copy());
            copy.keyframes.put(entry.getKey(), copyList);
        }
        return copy;
    }
}
