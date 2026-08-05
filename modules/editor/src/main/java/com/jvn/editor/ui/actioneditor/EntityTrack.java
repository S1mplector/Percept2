package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class EntityTrack {
    private static final double KEYFRAME_TIME_EPSILON_MS = 0.001;
    private static final PropertyType[] PROPERTY_TYPES = PropertyType.values();

    private final String entityName;
    private String parentGroupName;
    private final List<Keyframe>[] keyframes;
    private final EnumSet<PropertyType> animatedProperties;
    private final Map<String, List<Keyframe>> customKeyframes;
    private boolean expanded = true;
    private boolean visible = true;
    private boolean locked = false;
    private int layerOrder = 0;
    private int modCount = 0;

    public EntityTrack(String entityName) {
        this.entityName = entityName;
        this.keyframes = newKeyframeTable();
        this.animatedProperties = EnumSet.noneOf(PropertyType.class);
        this.customKeyframes = new java.util.LinkedHashMap<>();
    }

    public String getEntityName() { return entityName; }

    public String getParentGroupName() { return parentGroupName; }
    public void setParentGroupName(String parentGroupName) { this.parentGroupName = parentGroupName; }
    public boolean hasParent() { return parentGroupName != null && !parentGroupName.isBlank(); }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public int getLayerOrder() { return layerOrder; }
    public void setLayerOrder(int layerOrder) { this.layerOrder = layerOrder; }

    public int getModCount() { return modCount; }

    public List<Keyframe> getKeyframes(PropertyType property) {
        List<Keyframe> list = keyframesFor(property);
        return list != null ? list : Collections.emptyList();
    }

    public void addKeyframe(PropertyType property, Keyframe kf) {
        upsertKeyframe(property, kf);
    }

    public void addCustomKeyframe(String propertyKey, Keyframe kf) {
        upsertCustomKeyframe(propertyKey, kf);
    }

    public Keyframe upsertKeyframe(PropertyType property, Keyframe kf) {
        if (property == null || kf == null) return null;
        int propertyIndex = property.ordinal();
        List<Keyframe> list = keyframes[propertyIndex];
        if (list == null) {
            list = new ArrayList<>();
            keyframes[propertyIndex] = list;
            animatedProperties.add(property);
        }
        int matchingIndex = lowerBound(list, kf.getTimeMs() - KEYFRAME_TIME_EPSILON_MS);
        if (matchingIndex < list.size()) {
            Keyframe existing = list.get(matchingIndex);
            if (Math.abs(existing.getTimeMs() - kf.getTimeMs()) <= KEYFRAME_TIME_EPSILON_MS) {
                // Keep easing shape on existing keyframe; only replace value/time.
                existing.setTimeMs(kf.getTimeMs());
                existing.setValue(kf.getValue());
                modCount++;
                return existing;
            }
        }
        list.add(lowerBound(list, kf.getTimeMs()), kf);
        modCount++;
        return kf;
    }

    public Keyframe upsertCustomKeyframe(String propertyKey, Keyframe kf) {
        if (propertyKey == null || propertyKey.isBlank() || kf == null) return null;
        String normalized = propertyKey.trim();
        List<Keyframe> list = customKeyframes.computeIfAbsent(normalized, k -> new ArrayList<>());
        int matchingIndex = lowerBound(list, kf.getTimeMs() - KEYFRAME_TIME_EPSILON_MS);
        if (matchingIndex < list.size()) {
            Keyframe existing = list.get(matchingIndex);
            if (Math.abs(existing.getTimeMs() - kf.getTimeMs()) <= KEYFRAME_TIME_EPSILON_MS) {
                existing.setTimeMs(kf.getTimeMs());
                existing.setValue(kf.getValue());
                modCount++;
                return existing;
            }
        }
        list.add(lowerBound(list, kf.getTimeMs()), kf);
        modCount++;
        return kf;
    }

    public void removeKeyframe(PropertyType property, Keyframe kf) {
        List<Keyframe> list = keyframesFor(property);
        if (list != null) {
            if (list.remove(kf)) modCount++;
            if (list.isEmpty()) {
                keyframes[property.ordinal()] = null;
                animatedProperties.remove(property);
            }
        }
    }

    public void removeCustomKeyframe(String propertyKey, Keyframe kf) {
        if (propertyKey == null || propertyKey.isBlank()) return;
        List<Keyframe> list = customKeyframes.get(propertyKey.trim());
        if (list != null) {
            if (list.remove(kf)) modCount++;
            if (list.isEmpty()) customKeyframes.remove(propertyKey.trim());
        }
    }

    public void setKeyframes(PropertyType property, List<Keyframe> kfs) {
        if (property == null) return;
        modCount++;
        if (kfs == null || kfs.isEmpty()) {
            keyframes[property.ordinal()] = null;
            animatedProperties.remove(property);
        } else {
            List<Keyframe> copy = new ArrayList<>(kfs);
            Collections.sort(copy);
            keyframes[property.ordinal()] = copy;
            animatedProperties.add(property);
        }
    }

    public List<Keyframe> getCustomKeyframes(String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return Collections.emptyList();
        return customKeyframes.getOrDefault(propertyKey.trim(), Collections.emptyList());
    }

    public void setCustomKeyframes(String propertyKey, List<Keyframe> kfs) {
        if (propertyKey == null || propertyKey.isBlank()) return;
        String normalized = propertyKey.trim();
        modCount++;
        if (kfs == null || kfs.isEmpty()) {
            customKeyframes.remove(normalized);
        } else {
            customKeyframes.put(normalized, new ArrayList<>(kfs));
            sortCustomKeyframes(normalized);
        }
    }

    public void sortKeyframes(PropertyType property) {
        List<Keyframe> list = keyframesFor(property);
        if (list != null) Collections.sort(list);
    }

    public void sortCustomKeyframes(String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return;
        List<Keyframe> list = customKeyframes.get(propertyKey.trim());
        if (list != null) Collections.sort(list);
    }

    public Keyframe findKeyframeAt(PropertyType property, double timeMs) {
        List<Keyframe> list = keyframesFor(property);
        if (list == null || list.isEmpty()) return null;
        int index = lowerBound(list, timeMs - KEYFRAME_TIME_EPSILON_MS);
        if (index >= list.size()) return null;
        Keyframe keyframe = list.get(index);
        return Math.abs(keyframe.getTimeMs() - timeMs) <= KEYFRAME_TIME_EPSILON_MS ? keyframe : null;
    }

    public boolean hasKeyframes(PropertyType property) {
        List<Keyframe> list = keyframesFor(property);
        return list != null && !list.isEmpty();
    }

    public boolean hasCustomKeyframes(String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return false;
        List<Keyframe> list = customKeyframes.get(propertyKey.trim());
        return list != null && !list.isEmpty();
    }

    public Iterable<PropertyType> getAnimatedProperties() {
        return animatedProperties;
    }

    public Iterable<String> getAnimatedCustomProperties() {
        return customKeyframes.keySet();
    }

    public double getValueAt(PropertyType property, double timeMs) {
        if (property == null) return 0.0;
        List<Keyframe> list = keyframes[property.ordinal()];
        return interpolate(list, timeMs, property.getDefaultValue());
    }

    public double getCustomValueAt(String propertyKey, double timeMs, double defaultValue) {
        if (propertyKey == null || propertyKey.isBlank()) return defaultValue;
        return interpolate(customKeyframes.get(propertyKey.trim()), timeMs, defaultValue);
    }

    public double getMaxTimeMs() {
        double max = 0;
        for (PropertyType property : animatedProperties) {
            List<Keyframe> list = keyframes[property.ordinal()];
            for (Keyframe kf : list) {
                if (kf.getTimeMs() > max) max = kf.getTimeMs();
            }
        }
        for (List<Keyframe> list : customKeyframes.values()) {
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
        copy.locked = locked;
        copy.layerOrder = layerOrder;
        for (PropertyType property : animatedProperties) {
            List<Keyframe> copyList = new ArrayList<>();
            for (Keyframe kf : keyframes[property.ordinal()]) copyList.add(kf.copy());
            copy.keyframes[property.ordinal()] = copyList;
            copy.animatedProperties.add(property);
        }
        for (Map.Entry<String, List<Keyframe>> entry : customKeyframes.entrySet()) {
            List<Keyframe> copyList = new ArrayList<>();
            for (Keyframe kf : entry.getValue()) copyList.add(kf.copy());
            copy.customKeyframes.put(entry.getKey(), copyList);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Keyframe>[] newKeyframeTable() {
        return (List<Keyframe>[]) new List<?>[PROPERTY_TYPES.length];
    }

    private List<Keyframe> keyframesFor(PropertyType property) {
        return property != null ? keyframes[property.ordinal()] : null;
    }

    private static double interpolate(List<Keyframe> list, double timeMs, double defaultValue) {
        if (list == null || list.isEmpty()) return defaultValue;
        if (Double.isNaN(timeMs)) return defaultValue;
        if (timeMs <= list.get(0).getTimeMs()) return list.get(0).getValue();
        if (timeMs >= list.get(list.size() - 1).getTimeMs()) return list.get(list.size() - 1).getValue();

        int nextIndex = lowerBound(list, timeMs);
        Keyframe k0 = list.get(nextIndex - 1);
        Keyframe k1 = list.get(nextIndex);
        double span = k1.getTimeMs() - k0.getTimeMs();
        if (span < 0.001) return k1.getValue();
        double t = (timeMs - k0.getTimeMs()) / span;
        double easedT = com.jvn.core.animation.Easing.applyInterpolation(
            k1.getEasingSpec(), k1.getInterpolation(), t);
        return k0.getValue() + (k1.getValue() - k0.getValue()) * easedT;
    }

    private static int lowerBound(List<Keyframe> list, double timeMs) {
        int low = 0;
        int high = list.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (list.get(mid).getTimeMs() < timeMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
