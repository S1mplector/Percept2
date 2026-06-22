package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Persists reusable Puppeteer rig structure to
 * {@code <projectRoot>/config/puppeteer/rig.properties}.
 *
 * <p>This store is intentionally structural: it saves group hierarchy, track
 * membership/presentation state, and layer-link constraints. Timeline
 * keyframes stay in JES exports/drafts so shot animation does not leak into
 * every new scene launch.
 */
public final class PuppeteerRigStore {

    private static final String FILENAME = "rig.properties";

    private PuppeteerRigStore() {}

    public static void save(File projectRoot, AnimationProject project) {
        if (projectRoot == null || project == null) return;
        List<EntityGroup> groups = sortedGroups(project);
        Map<String, Constraint> constraints = project.getConstraintsView();
        if (groups.isEmpty() && constraints.isEmpty()) {
            deleteFile(projectRoot);
            return;
        }

        Properties props = new Properties();
        props.setProperty("rig.version", "1");

        int groupIndex = 0;
        for (EntityGroup group : groups) {
            if (group == null || isBlank(group.getName())) continue;
            String pfx = "group." + groupIndex;
            props.setProperty(pfx + ".name", group.getName());
            props.setProperty(pfx + ".parent", safe(group.getParentGroupName()));
            props.setProperty(pfx + ".layer", Integer.toString(group.getLayerOrder()));
            props.setProperty(pfx + ".expanded", Boolean.toString(group.isExpanded()));
            props.setProperty(pfx + ".locked", Boolean.toString(group.isLocked()));
            groupIndex++;
        }
        props.setProperty("group.count", Integer.toString(groupIndex));

        int trackIndex = 0;
        for (EntityTrack track : project.getTracks()) {
            if (track == null || isBlank(track.getEntityName())) continue;
            if (!track.hasParent() && track.isVisible() && track.isExpanded()
                    && !track.isLocked() && track.getLayerOrder() == 0) {
                continue;
            }
            String pfx = "track." + trackIndex;
            props.setProperty(pfx + ".name", track.getEntityName());
            props.setProperty(pfx + ".parent", safe(track.getParentGroupName()));
            props.setProperty(pfx + ".visible", Boolean.toString(track.isVisible()));
            props.setProperty(pfx + ".expanded", Boolean.toString(track.isExpanded()));
            props.setProperty(pfx + ".locked", Boolean.toString(track.isLocked()));
            props.setProperty(pfx + ".layer", Integer.toString(track.getLayerOrder()));
            trackIndex++;
        }
        props.setProperty("track.count", Integer.toString(trackIndex));

        int constraintIndex = 0;
        for (Map.Entry<String, Constraint> entry : constraints.entrySet()) {
            String target = entry.getKey();
            Constraint constraint = entry.getValue();
            if (isBlank(target) || constraint == null || constraint.getType() == null
                    || isBlank(constraint.getTargetEntityName())) {
                continue;
            }
            String pfx = "constraint." + constraintIndex;
            props.setProperty(pfx + ".target", target);
            props.setProperty(pfx + ".type", constraint.getType().name());
            props.setProperty(pfx + ".source", constraint.getTargetEntityName());
            props.setProperty(pfx + ".offsetX", Double.toString(constraint.getOffsetX()));
            props.setProperty(pfx + ".offsetY", Double.toString(constraint.getOffsetY()));
            props.setProperty(pfx + ".inheritRot", Boolean.toString(constraint.isInheritRotation()));
            props.setProperty(pfx + ".inheritScale", Boolean.toString(constraint.isInheritScale()));
            constraintIndex++;
        }
        props.setProperty("constraint.count", Integer.toString(constraintIndex));

        try {
            Path dir = projectRoot.toPath().resolve("config").resolve("puppeteer");
            Files.createDirectories(dir);
            try (OutputStream out = Files.newOutputStream(dir.resolve(FILENAME))) {
                props.store(out, "Puppeteer reusable rig structure");
            }
        } catch (IOException ignored) {
            // best-effort persistence; in-memory rig state remains valid
        }
    }

    public static void load(File projectRoot, AnimationProject project) {
        if (projectRoot == null || project == null) return;
        File file = resolveFile(projectRoot);
        if (file == null || !file.isFile()) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException ignored) {
            return;
        }

        int groupCount = parseInt(props.getProperty("group.count"), 0);
        Map<String, String> groupParents = new LinkedHashMap<>();
        for (int i = 0; i < groupCount; i++) {
            String pfx = "group." + i;
            String name = props.getProperty(pfx + ".name", "").trim();
            if (name.isEmpty()) continue;
            groupParents.put(name, props.getProperty(pfx + ".parent", "").trim());
        }

        int trackCount = parseInt(props.getProperty("track.count"), 0);
        Set<String> groupsToRestore = new LinkedHashSet<>();
        for (int i = 0; i < trackCount; i++) {
            String pfx = "track." + i;
            String name = props.getProperty(pfx + ".name", "").trim();
            if (name.isEmpty() || project.getTrack(name) == null) continue;
            includeGroupAncestors(
                props.getProperty(pfx + ".parent", "").trim(),
                groupParents,
                groupsToRestore);
        }

        project.clearGroups();
        project.clearConstraints();

        for (int i = 0; i < groupCount; i++) {
            String pfx = "group." + i;
            String name = props.getProperty(pfx + ".name", "").trim();
            if (name.isEmpty() || !groupsToRestore.contains(name)) continue;
            EntityGroup group = project.getOrCreateGroup(name);
            group.setLayerOrder(parseInt(props.getProperty(pfx + ".layer"), 0));
            group.setExpanded(parseBoolean(props.getProperty(pfx + ".expanded"), true));
            group.setLocked(parseBoolean(props.getProperty(pfx + ".locked"), false));
        }
        for (int i = 0; i < groupCount; i++) {
            String pfx = "group." + i;
            String name = props.getProperty(pfx + ".name", "").trim();
            String parent = props.getProperty(pfx + ".parent", "").trim();
            if (name.isEmpty() || parent.isEmpty()
                    || !groupsToRestore.contains(name)
                    || !groupsToRestore.contains(parent)) continue;
            project.getOrCreateGroup(parent);
            project.addGroupToGroup(name, parent);
        }

        for (int i = 0; i < trackCount; i++) {
            String pfx = "track." + i;
            String name = props.getProperty(pfx + ".name", "").trim();
            EntityTrack track = name.isEmpty() ? null : project.getTrack(name);
            if (track == null) continue;
            track.setVisible(parseBoolean(props.getProperty(pfx + ".visible"), true));
            track.setExpanded(parseBoolean(props.getProperty(pfx + ".expanded"), true));
            track.setLocked(parseBoolean(props.getProperty(pfx + ".locked"), false));
            track.setLayerOrder(parseInt(props.getProperty(pfx + ".layer"), 0));
            String parent = props.getProperty(pfx + ".parent", "").trim();
            if (!parent.isEmpty() && groupsToRestore.contains(parent)) {
                project.getOrCreateGroup(parent);
                project.addEntityToGroup(name, parent);
            }
        }

        int constraintCount = parseInt(props.getProperty("constraint.count"), 0);
        for (int i = 0; i < constraintCount; i++) {
            String pfx = "constraint." + i;
            String target = props.getProperty(pfx + ".target", "").trim();
            String source = props.getProperty(pfx + ".source", "").trim();
            Constraint.Type type = parseConstraintType(props.getProperty(pfx + ".type"));
            if (target.isEmpty() || source.isEmpty() || type == null
                    || project.getTrack(target) == null
                    || project.getTrack(source) == null) continue;
            Constraint constraint = new Constraint(
                type,
                source,
                parseDouble(props.getProperty(pfx + ".offsetX"), 0.0),
                parseDouble(props.getProperty(pfx + ".offsetY"), 0.0),
                parseBoolean(props.getProperty(pfx + ".inheritRot"), true),
                parseBoolean(props.getProperty(pfx + ".inheritScale"), true)
            );
            project.setConstraint(target, constraint);
        }
    }

    private static void includeGroupAncestors(
            String groupName,
            Map<String, String> groupParents,
            Set<String> groupsToRestore) {
        String current = groupName;
        while (!isBlank(current) && groupsToRestore.add(current)) {
            current = groupParents.get(current);
        }
    }

    private static List<EntityGroup> sortedGroups(AnimationProject project) {
        List<EntityGroup> groups = new ArrayList<>();
        for (EntityGroup group : project.getGroups()) {
            if (group != null) groups.add(group);
        }
        groups.sort(Comparator.comparing(EntityGroup::getName, String.CASE_INSENSITIVE_ORDER));
        return groups;
    }

    private static void deleteFile(File projectRoot) {
        try {
            File file = resolveFile(projectRoot);
            if (file != null && file.isFile()) file.delete();
        } catch (Exception ignored) {
            // non-critical cleanup
        }
    }

    private static File resolveFile(File projectRoot) {
        if (projectRoot == null) return null;
        return projectRoot.toPath()
            .resolve("config")
            .resolve("puppeteer")
            .resolve(FILENAME)
            .toFile();
    }

    private static Constraint.Type parseConstraintType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Constraint.Type.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) return true;
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) return false;
        return fallback;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
