package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CodeExporter {

    public static String export(AnimationProject project) {
        if (project == null) return "timeline {\n}\n";

        StringBuilder sb = new StringBuilder();
        sb.append("timeline {\n");

        List<TimelineEvent> events = collectEvents(project);
        events.sort(Comparator.comparingDouble(e -> e.startTime));

        double currentTime = 0;

        for (TimelineEvent event : events) {
            if (event.startTime > currentTime + 0.5) {
                double waitMs = event.startTime - currentTime;
                sb.append("  wait ").append(formatNumber(waitMs)).append("\n");
            }

            sb.append(formatEvent(event));
            currentTime = event.startTime;
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

        for (EntityTrack track : project.getTracks()) {
            String entity = track.getEntityName();

            collectPropertyEvents(events, entity, track, PropertyType.X, PropertyType.Y, "move");
            collectPropertyEvents(events, entity, track, PropertyType.ROTATION, null, "rotate");
            collectPropertyEvents(events, entity, track, PropertyType.SCALE_X, PropertyType.SCALE_Y, "scale");
            collectPropertyEvents(events, entity, track, PropertyType.ALPHA, null, "fade");
        }

        for (EntityGroup group : project.getGroups()) {
            EntityTrack gt = group.getGroupTrack();
            String groupName = group.getName();

            collectPropertyEvents(events, groupName, gt, PropertyType.X, PropertyType.Y, "move");
            collectPropertyEvents(events, groupName, gt, PropertyType.ROTATION, null, "rotate");
        }

        return events;
    }

    private static void collectPropertyEvents(List<TimelineEvent> events, String target,
                                               EntityTrack track, PropertyType p1, PropertyType p2,
                                               String actionType) {
        List<Keyframe> list1 = track.getKeyframes(p1);
        List<Keyframe> list2 = p2 != null ? track.getKeyframes(p2) : null;

        if (list1.isEmpty() && (list2 == null || list2.isEmpty())) return;

        List<Double> times = new ArrayList<>();
        for (Keyframe kf : list1) {
            if (!times.contains(kf.getTimeMs())) times.add(kf.getTimeMs());
        }
        if (list2 != null) {
            for (Keyframe kf : list2) {
                if (!times.contains(kf.getTimeMs())) times.add(kf.getTimeMs());
            }
        }
        times.sort(Double::compare);

        for (int i = 0; i < times.size() - 1; i++) {
            double startTime = times.get(i);
            double endTime = times.get(i + 1);
            double duration = endTime - startTime;

            double startVal1 = track.getValueAt(p1, startTime);
            double endVal1 = track.getValueAt(p1, endTime);
            double startVal2 = p2 != null ? track.getValueAt(p2, startTime) : 0;
            double endVal2 = p2 != null ? track.getValueAt(p2, endTime) : 0;

            Easing.Type easing = findEasingAt(list1, endTime);
            if (easing == null && list2 != null) easing = findEasingAt(list2, endTime);
            if (easing == null) easing = Easing.Type.LINEAR;

            boolean changed = Math.abs(endVal1 - startVal1) > 0.001 ||
                             (p2 != null && Math.abs(endVal2 - startVal2) > 0.001);
            if (!changed) continue;

            TimelineEvent ev = new TimelineEvent();
            ev.actionType = actionType;
            ev.target = target;
            ev.startTime = startTime;
            ev.duration = duration;
            ev.easing = easing;

            switch (actionType) {
                case "move" -> {
                    ev.props.put("x", endVal1);
                    if (p2 != null) ev.props.put("y", endVal2);
                }
                case "rotate" -> ev.props.put("deg", endVal1);
                case "scale" -> {
                    ev.props.put("sx", endVal1);
                    if (p2 != null) ev.props.put("sy", endVal2);
                }
                case "fade" -> ev.props.put("alpha", endVal1);
            }

            events.add(ev);
        }
    }

    private static Easing.Type findEasingAt(List<Keyframe> list, double time) {
        for (Keyframe kf : list) {
            if (Math.abs(kf.getTimeMs() - time) < 0.5) return kf.getEasing();
        }
        return null;
    }

    private static String formatEvent(TimelineEvent ev) {
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(ev.actionType).append(" \"").append(ev.target).append("\" {\n");

        for (Map.Entry<String, Double> entry : ev.props.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(": ")
              .append(formatNumber(entry.getValue())).append("\n");
        }

        sb.append("    dur: ").append(formatNumber(ev.duration)).append("\n");

        if (ev.easing != Easing.Type.LINEAR) {
            sb.append("    easing: ").append(ev.easing.name().toLowerCase()).append("\n");
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

    private static class TimelineEvent {
        String actionType;
        String target;
        double startTime;
        double duration;
        Easing.Type easing;
        Map<String, Double> props = new java.util.LinkedHashMap<>();
    }
}
