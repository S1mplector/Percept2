package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

public class CodeExporter {
    private static final double TIME_QUANTIZATION_FACTOR = 1000.0; // 0.001ms

    public static String exportNamed(AnimationProject project, String name) {
        String body = export(project);
        StringBuilder sb = new StringBuilder();
        sb.append("// Timeline: ").append(name).append("\n");
        sb.append("// Usage in VNS: @external jes_timeline ").append(name).append("\n\n");
        sb.append(body);
        return sb.toString();
    }

    private static List<Double> collectUniqueTimes(List<Keyframe> list1, List<Keyframe> list2) {
        Map<Long, Double> timeMap = new TreeMap<>();
        for (Keyframe kf : list1) {
            long key = Math.round(kf.getTimeMs() * TIME_QUANTIZATION_FACTOR);
            timeMap.putIfAbsent(key, kf.getTimeMs());
        }
        if (list2 != null) {
            for (Keyframe kf : list2) {
                long key = Math.round(kf.getTimeMs() * TIME_QUANTIZATION_FACTOR);
                timeMap.putIfAbsent(key, kf.getTimeMs());
            }
        }
        return new ArrayList<>(timeMap.values());
    }

    public static String export(AnimationProject project) {
        if (project == null) return "timeline {\n}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        Map<Double, List<TimelineEvent>> byTime = new TreeMap<>();
        for (TimelineEvent e : events) {
            double rounded = Math.round(e.startTime * 10.0) / 10.0;
            byTime.computeIfAbsent(rounded, k -> new ArrayList<>()).add(e);
        }

        double currentTime = 0;
        for (Map.Entry<Double, List<TimelineEvent>> entry : byTime.entrySet()) {
            double time = entry.getKey();
            List<TimelineEvent> group = entry.getValue();

            if (time > currentTime + 0.5) {
                sb.append("  wait ").append(formatNumber(time - currentTime)).append("\n");
            }

            if (group.size() == 1) {
                sb.append(formatEvent(group.get(0)));
            } else {
                sb.append("  parallel {\n");
                for (TimelineEvent ev : group) {
                    sb.append("  ").append(formatEvent(ev));
                }
                sb.append("  }\n");
            }
            currentTime = time;
        }

        sb.append("}\n");
        return sb.toString();
    }

    public static String exportWithGroups(AnimationProject project) {
        if (project == null) return "timeline {\n}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        for (String groupName : project.getRootGroupNames()) {
            exportGroupRecursive(sb, project, groupName, "  ");
        }

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        Map<Double, List<TimelineEvent>> byTime = new TreeMap<>();
        for (TimelineEvent e : events) {
            byTime.computeIfAbsent(e.startTime, k -> new ArrayList<>()).add(e);
        }

        double currentTime = 0;
        for (Map.Entry<Double, List<TimelineEvent>> entry : byTime.entrySet()) {
            double time = entry.getKey();
            List<TimelineEvent> group = entry.getValue();

            if (time > currentTime + 0.5) {
                sb.append("  wait ").append(formatNumber(time - currentTime)).append("\n");
            }

            if (group.size() == 1) {
                sb.append(formatEvent(group.get(0)));
            } else {
                sb.append("  parallel {\n");
                for (TimelineEvent ev : group) {
                    sb.append("  ").append(formatEvent(ev));
                }
                sb.append("  }\n");
            }
            currentTime = time;
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static void exportGroupRecursive(StringBuilder sb, AnimationProject project, 
                                              String groupName, String indent) {
        EntityGroup group = project.getGroup(groupName);
        if (group == null) return;

        sb.append(indent).append("// Group: ").append(groupName).append("\n");

        for (String childGroup : group.getChildGroupNames()) {
            exportGroupRecursive(sb, project, childGroup, indent);
        }
    }

    private static List<TimelineEvent> collectEvents(AnimationProject project) {
        List<TimelineEvent> events = new ArrayList<>();
        EntityTrack cameraTrack = null;

        for (EntityTrack track : project.getTracks()) {
            String entity = track.getEntityName();

            collectPropertyEvents(events, project, entity, track, PropertyType.X, PropertyType.Y, "move", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.Z, null, "depth", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.PIVOT_X, PropertyType.PIVOT_Y, "pivot", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.ROTATION, null, "rotate", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.SCALE_X, PropertyType.SCALE_Y, "scale", true);
            collectPropertyEvents(events, project, entity, track, PropertyType.ALPHA, null, "fade", true);
            collectVisibilityEvents(events, entity, track);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MXX);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MXY);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MYX);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_MYY);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_TX);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.MATRIX_TY);
            collectTimelineCustomPropertyEvents(events, entity, track, PropertyType.BLUR);
            collectCustomPropertyEvents(events, entity, track);

            if (cameraTrack == null &&
                (track.hasKeyframes(PropertyType.CAMERA_X)
                    || track.hasKeyframes(PropertyType.CAMERA_Y)
                    || track.hasKeyframes(PropertyType.CAMERA_ZOOM)
                    || track.hasKeyframes(PropertyType.CAMERA_DOF_FOCUS)
                    || track.hasKeyframes(PropertyType.CAMERA_DOF_STRENGTH)
                    || track.hasKeyframes(PropertyType.CAMERA_DOF_MAX_BLUR)
                    || hasAnyCustomKeys(track))) {
                cameraTrack = track;
            }
        }

        if (cameraTrack != null) {
            collectPropertyEvents(events, null, "__camera__", cameraTrack, PropertyType.CAMERA_X, PropertyType.CAMERA_Y, "cameraMove", false);
            collectPropertyEvents(events, null, "__camera__", cameraTrack, PropertyType.CAMERA_ZOOM, null, "cameraZoom", false);
            collectTimelineCustomPropertyEvents(events, "__camera__", cameraTrack, PropertyType.CAMERA_DOF_FOCUS);
            collectTimelineCustomPropertyEvents(events, "__camera__", cameraTrack, PropertyType.CAMERA_DOF_STRENGTH);
            collectTimelineCustomPropertyEvents(events, "__camera__", cameraTrack, PropertyType.CAMERA_DOF_MAX_BLUR);
            collectCustomPropertyEvents(events, "__camera__", cameraTrack);
        }

        for (AudioCue cue : project.getAudioCues()) {
            if (cue == null || cue.getAudioFile() == null || cue.getAudioFile().isBlank()) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = "playAudio";
            ev.target = cue.getAudioFile().trim();
            ev.startTime = Math.max(0, cue.getTimeMs());
            ev.duration = 0;
            ev.props.put("volume", cue.getVolume());
            boolean isMusic = "music".equalsIgnoreCase(cue.getChannel());
            ev.props.put("loop", isMusic);
            ev.props.put("bgm", isMusic);
            events.add(ev);
        }

        for (EditorEventCue evt : project.getEditorEventCues()) {
            if (evt == null || evt.getType().isBlank()) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = mapActionTypeForEventCue(evt);
            ev.target = resolveActionTarget(evt, ev.actionType);
            ev.startTime = Math.max(0, evt.getTimeMs());
            ev.duration = 0;
            for (Map.Entry<String, String> entry : evt.getPayloadView().entrySet()) {
                if (!"target".equals(entry.getKey()) || "event".equals(ev.actionType)) {
                    ev.props.put(entry.getKey(), entry.getValue());
                }
            }
            if ("event".equals(ev.actionType)) {
                ev.target = evt.getType();
            }
            events.add(ev);
        }

        return events;
    }

    private static String mapActionTypeForEventCue(EditorEventCue cue) {
        if (cue == null || cue.getType() == null) return "event";
        String normalized = cue.getType().trim().toLowerCase();
        if ("scene".equals(normalized)) return cue.getType().trim();
        if (("expression".equals(normalized)
            || "show".equals(normalized)
            || "hide".equals(normalized)
            || "replace".equals(normalized))
            && !cue.getPayloadValue("target").isBlank()) {
            return cue.getType().trim();
        }
        return "event";
    }

    private static String resolveActionTarget(EditorEventCue cue, String actionType) {
        if (cue == null || actionType == null) return "";
        if ("scene".equalsIgnoreCase(actionType)) {
            return cue.getPayloadValue("target");
        }
        if ("event".equalsIgnoreCase(actionType)) {
            return cue.getType();
        }
        return cue.getPayloadValue("target");
    }

    private static void collectPropertyEvents(
        List<TimelineEvent> events,
        AnimationProject project,
        String target,
        EntityTrack track,
        PropertyType p1,
        PropertyType p2,
        String actionType,
        boolean effective
    ) {
        if (track == null) return;
        List<Keyframe> list1 = effective && project != null ? project.getEffectiveKeyframes(target, p1) : track.getKeyframes(p1);
        List<Keyframe> list2 = p2 != null
            ? (effective && project != null ? project.getEffectiveKeyframes(target, p2) : track.getKeyframes(p2))
            : null;
        boolean hasP1 = !list1.isEmpty();
        boolean hasP2 = p2 != null && list2 != null && !list2.isEmpty();

        if (!hasP1 && !hasP2) return;

        List<Double> times = collectUniqueTimes(list1, list2);
        if (times.size() == 1 && "depth".equals(actionType) && hasP1) {
            double time = times.get(0);
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = actionType;
            ev.target = target;
            ev.startTime = time;
            ev.duration = 0.0;
            ev.props.put("z", track.getValueAt(p1, time));
            events.add(ev);
            return;
        }

        for (int i = 0; i < times.size() - 1; i++) {
            double startTime = times.get(i);
            double endTime = times.get(i + 1);
            double duration = endTime - startTime;

            double startVal1 = effective && project != null
                ? project.computeValueAt(target, p1, startTime, p1.getDefaultValue())
                : track.getValueAt(p1, startTime);
            double endVal1 = effective && project != null
                ? project.computeValueAt(target, p1, endTime, p1.getDefaultValue())
                : track.getValueAt(p1, endTime);
            double startVal2 = p2 != null
                ? (effective && project != null
                    ? project.computeValueAt(target, p2, startTime, p2.getDefaultValue())
                    : track.getValueAt(p2, startTime))
                : 0;
            double endVal2 = p2 != null
                ? (effective && project != null
                    ? project.computeValueAt(target, p2, endTime, p2.getDefaultValue())
                    : track.getValueAt(p2, endTime))
                : 0;

            EasingSpec easingSpec = findEasingSpecAt(list1, endTime);
            if (easingSpec == null && list2 != null) easingSpec = findEasingSpecAt(list2, endTime);
            if (easingSpec == null) easingSpec = EasingSpec.of(Easing.Type.LINEAR);

            boolean changed = (hasP1 && Math.abs(endVal1 - startVal1) > 0.001) ||
                             (hasP2 && Math.abs(endVal2 - startVal2) > 0.001);
            if (!changed) continue;

            Keyframe endKf = findKeyframeAt(list1, endTime);
            if (endKf == null && list2 != null) endKf = findKeyframeAt(list2, endTime);

            TimelineEvent ev = new TimelineEvent();
            ev.actionType = actionType;
            ev.target = target;
            ev.startTime = startTime;
            ev.duration = duration;
            ev.easingSpec = easingSpec;
            ev.interpolation = endKf != null
                ? endKf.getInterpolation()
                : Easing.Interpolation.TWEEN;

            switch (actionType) {
                case "move" -> {
                    if (hasP1) ev.props.put("x", endVal1);
                    if (hasP2) ev.props.put("y", endVal2);
                }
                case "depth" -> {
                    if (hasP1) ev.props.put("z", endVal1);
                }
                case "pivot" -> {
                    if (hasP1) ev.props.put("ox", endVal1);
                    if (hasP2) ev.props.put("oy", endVal2);
                }
                case "rotate" -> {
                    if (hasP1) ev.props.put("deg", endVal1);
                }
                case "scale" -> {
                    if (hasP1) ev.props.put("sx", endVal1);
                    if (hasP2) ev.props.put("sy", endVal2);
                }
                case "fade" -> {
                    if (hasP1) ev.props.put("alpha", endVal1);
                }
                case "cameraMove" -> {
                    if (hasP1) ev.props.put("x", endVal1);
                    if (hasP2) ev.props.put("y", endVal2);
                }
                case "cameraZoom" -> {
                    if (hasP1) ev.props.put("zoom", endVal1);
                }
            }

            events.add(ev);
        }
    }

    private static void collectVisibilityEvents(List<TimelineEvent> events, String target, EntityTrack track) {
        if (track == null) return;
        List<Keyframe> keyframes = track.getKeyframes(PropertyType.VISIBILITY);
        if (keyframes.isEmpty()) return;

        double previous = PropertyType.VISIBILITY.getDefaultValue();
        for (Keyframe keyframe : keyframes) {
            if (keyframe == null) continue;
            double current = keyframe.getValue();
            if (Math.abs(current - previous) <= 0.001) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = "visible";
            ev.target = target;
            ev.startTime = Math.max(0.0, keyframe.getTimeMs());
            ev.props.put("value", current >= 0.5);
            events.add(ev);
            previous = current;
        }
    }

    private static void collectTimelineCustomPropertyEvents(
        List<TimelineEvent> events,
        String target,
        EntityTrack track,
        PropertyType property
    ) {
        if (track == null || property == null || !property.isTimelineCustomProperty()) return;
        List<Keyframe> keyframes = track.getKeyframes(property);
        if (keyframes.isEmpty()) {
            collectCustomPropertyEvents(events, target, track, property.getTimelineCustomKey(), property.getDefaultValue());
            return;
        }

        double previous = property.getDefaultValue();
        for (Keyframe keyframe : keyframes) {
            if (keyframe == null) continue;
            double current = keyframe.getValue();
            if (Math.abs(current - previous) <= 0.001 && keyframe.getTimeMs() > 0.001) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = "property";
            ev.target = target;
            ev.startTime = Math.max(0.0, keyframe.getTimeMs());
            ev.props.put("key", property.getTimelineCustomKey());
            ev.props.put("value", current);
            events.add(ev);
            previous = current;
        }
    }

    private static void collectCustomPropertyEvents(List<TimelineEvent> events, String target, EntityTrack track) {
        if (track == null) return;
        for (String propertyKey : track.getAnimatedCustomProperties()) {
            if (propertyKey == null || propertyKey.isBlank()) continue;
            collectCustomPropertyEvents(events, target, track, propertyKey, defaultCustomPropertyValue(target, propertyKey));
        }
    }

    private static void collectCustomPropertyEvents(
        List<TimelineEvent> events,
        String target,
        EntityTrack track,
        String propertyKey,
        double defaultValue
    ) {
        if (track == null || propertyKey == null || propertyKey.isBlank()) return;
        List<Keyframe> keyframes = track.getCustomKeyframes(propertyKey);
        if (keyframes.isEmpty()) return;

        double previous = defaultValue;
        for (Keyframe keyframe : keyframes) {
            if (keyframe == null) continue;
            double current = keyframe.getValue();
            if (Math.abs(current - previous) <= 0.001 && keyframe.getTimeMs() > 0.001) continue;
            TimelineEvent ev = new TimelineEvent();
            ev.actionType = "property";
            ev.target = target;
            ev.startTime = Math.max(0.0, keyframe.getTimeMs());
            ev.props.put("key", propertyKey);
            ev.props.put("value", current);
            events.add(ev);
            previous = current;
        }
    }

    private static EasingSpec findEasingSpecAt(List<Keyframe> list, double time) {
        for (Keyframe kf : list) {
            if (Math.abs(kf.getTimeMs() - time) < 0.5) return kf.getEasingSpec();
        }
        return null;
    }

    private static Keyframe findKeyframeAt(List<Keyframe> list, double time) {
        for (Keyframe kf : list) {
            if (Math.abs(kf.getTimeMs() - time) < 0.5) return kf;
        }
        return null;
    }

    private static String formatEvent(TimelineEvent ev) {
        StringBuilder sb = new StringBuilder();
        if ("cameraMove".equals(ev.actionType) || "cameraZoom".equals(ev.actionType)) {
            sb.append("  ").append(ev.actionType).append(" {\n");
        } else if ("scene".equals(ev.actionType) && (ev.target == null || ev.target.isBlank())) {
            sb.append("  scene {\n");
        } else if ("event".equals(ev.actionType)) {
            sb.append("  event \"").append(ev.target).append("\" {\n");
        } else if ("property".equals(ev.actionType) && "__camera__".equals(ev.target)) {
            sb.append("  property {\n");
        } else {
            sb.append("  ").append(ev.actionType).append(" \"").append(ev.target).append("\" {\n");
        }

        for (Map.Entry<String, Object> entry : ev.props.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(": ")
              .append(formatPropValue(entry.getValue())).append("\n");
        }

        if (ev.duration > 0.0 && !"playAudio".equals(ev.actionType)) {
            sb.append("    dur: ").append(formatNumber(ev.duration)).append("\n");
        }

        if (ev.interpolation != Easing.Interpolation.TWEEN) {
            sb.append("    interp: ").append(ev.interpolation.name().toLowerCase()).append("\n");
        }

        if (ev.interpolation == Easing.Interpolation.TWEEN
            && ev.easingSpec != null
            && ev.easingSpec.getType() != Easing.Type.LINEAR) {
            sb.append("    easing: ").append(ev.easingSpec.toDslString()).append("\n");
        }

        sb.append("  }\n");
        return sb.toString();
    }

    private static String formatNumber(double v) {
        if (Math.abs(v - Math.round(v)) < 0.0001) {
            return Long.toString(Math.round(v));
        }
        return String.format("%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatPropValue(Object value) {
        if (value instanceof Number n) return formatNumber(n.doubleValue());
        if (value instanceof Boolean b) return b ? "true" : "false";
        if (value == null) return "\"\"";
        String text = value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + text + "\"";
    }

    public static String exportIncremental(AnimationProject project) {
        if (project == null) return "timeline {\n}\n";

        Map<String, Map<PropertyType, Double>> snapshot = project.getInitialSnapshot();
        if (snapshot == null) return export(project);

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        List<TimelineEvent> filtered = new ArrayList<>();
        for (TimelineEvent ev : events) {
            boolean changed = false;
            Map<PropertyType, Double> initial = snapshot.get(ev.target);
            if (initial == null) { changed = true; }
            else {
                for (Map.Entry<String, Object> prop : ev.props.entrySet()) {
                    PropertyType pt = findPropertyByCode(prop.getKey());
                    if (pt != null && prop.getValue() instanceof Number n) {
                        Double initVal = initial.get(pt);
                        if (initVal == null || Math.abs(initVal - n.doubleValue()) > 0.001) {
                            changed = true;
                            break;
                        }
                    } else {
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) filtered.add(ev);
        }

        Map<Double, List<TimelineEvent>> byTime = new TreeMap<>();
        for (TimelineEvent e : filtered) {
            double rounded = Math.round(e.startTime * 10.0) / 10.0;
            byTime.computeIfAbsent(rounded, k -> new ArrayList<>()).add(e);
        }

        double currentTime = 0;
        for (Map.Entry<Double, List<TimelineEvent>> entry : byTime.entrySet()) {
            double time = entry.getKey();
            List<TimelineEvent> group = entry.getValue();

            if (time > currentTime + 0.5) {
                sb.append("  wait ").append(formatNumber(time - currentTime)).append("\n");
            }

            if (group.size() == 1) {
                sb.append(formatEvent(group.get(0)));
            } else {
                sb.append("  parallel {\n");
                for (TimelineEvent ev : group) {
                    sb.append("  ").append(formatEvent(ev));
                }
                sb.append("  }\n");
            }
            currentTime = time;
        }

        sb.append("}\n");
        return sb.toString();
    }

    public static String exportAudioCues(AnimationProject project) {
        if (project == null) return "";
        List<AudioCue> cues = project.getAudioCues();
        if (cues.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (AudioCue cue : cues) {
            sb.append("at ").append(formatNumber(cue.getTimeMs())).append("ms: ");
            sb.append("play ").append(cue.getChannel()).append(" \"").append(cue.getAudioFile()).append("\"");
            if (cue.getVolume() < 1.0) {
                sb.append(" volume ").append(formatNumber(cue.getVolume()));
            }
            if (cue.isFadeIn() && cue.getFadeDurationMs() > 0) {
                sb.append(" fadein ").append(formatNumber(cue.getFadeDurationMs()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static PropertyType findPropertyByCode(String code) {
        for (PropertyType p : PropertyType.values()) {
            if (p.getCode().equals(code)) return p;
        }
        switch (code) {
            case "x": return PropertyType.X;
            case "y": return PropertyType.Y;
            case "z": return PropertyType.Z;
            case "ox": return PropertyType.PIVOT_X;
            case "oy": return PropertyType.PIVOT_Y;
            case "deg": return PropertyType.ROTATION;
            case "sx": return PropertyType.SCALE_X;
            case "sy": return PropertyType.SCALE_Y;
            case "alpha": return PropertyType.ALPHA;
            case "visible":
            case "value": return PropertyType.VISIBILITY;
            default: return null;
        }
    }

    /**
     * Compact export: emits a single-line-per-event format for minimal file size.
     */
    public static String exportCompact(AnimationProject project) {
        if (project == null) return "timeline {}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        double currentTime = 0;
        for (TimelineEvent ev : events) {
            double time = Math.round(ev.startTime * 10.0) / 10.0;
            if (time > currentTime + 0.5) {
                sb.append("  wait ").append(formatNumber(time - currentTime)).append("\n");
                currentTime = time;
            }
            sb.append("  ").append(formatCompactEvent(ev)).append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String formatCompactEvent(TimelineEvent ev) {
        StringBuilder sb = new StringBuilder();
        if ("event".equals(ev.actionType)) {
            sb.append("event \"").append(ev.target).append("\"");
        } else if ("property".equals(ev.actionType) && "__camera__".equals(ev.target)) {
            sb.append("property");
        } else if ("cameraMove".equals(ev.actionType) || "cameraZoom".equals(ev.actionType)) {
            sb.append(ev.actionType);
        } else {
            sb.append(ev.actionType).append(" \"").append(ev.target).append("\"");
        }
        sb.append(" {");
        for (Map.Entry<String, Object> entry : ev.props.entrySet()) {
            sb.append(" ").append(entry.getKey()).append(":").append(formatPropValue(entry.getValue()));
        }
        if (ev.duration > 0.0 && !"playAudio".equals(ev.actionType)) {
            sb.append(" dur:").append(formatNumber(ev.duration));
        }
        if (ev.interpolation != Easing.Interpolation.TWEEN) {
            sb.append(" interp:").append(ev.interpolation.name().toLowerCase());
        }
        if (ev.interpolation == Easing.Interpolation.TWEEN
            && ev.easingSpec != null
            && ev.easingSpec.getType() != com.jvn.core.animation.Easing.Type.LINEAR) {
            sb.append(" easing:").append(ev.easingSpec.toDslString());
        }
        sb.append(" }");
        return sb.toString();
    }

    private static class TimelineEvent {
        String actionType;
        String target;
        double startTime;
        double duration;
        EasingSpec easingSpec = EasingSpec.of(Easing.Type.LINEAR);
        Easing.Interpolation interpolation = Easing.Interpolation.TWEEN;
        Map<String, Object> props = new java.util.LinkedHashMap<>();
    }

    private static boolean hasAnyCustomKeys(EntityTrack track) {
        if (track == null) return false;
        return track.getAnimatedCustomProperties().iterator().hasNext();
    }

    private static double defaultCustomPropertyValue(String target, String propertyKey) {
        if (propertyKey == null || propertyKey.isBlank()) return 0.0;
        if ("__camera__".equals(target)) {
            var definition = com.jvn.core.graphics.Camera2D.getAnimatableProperty(propertyKey);
            return definition != null ? definition.getDefaultValue() : 0.0;
        }
        var definition = com.jvn.core.scene2d.Entity2D.getAnimatableProperty(propertyKey);
        return definition != null ? definition.getDefaultValue() : 0.0;
    }
}
