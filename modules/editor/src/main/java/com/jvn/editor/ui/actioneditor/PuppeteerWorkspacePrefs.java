package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Persists Puppeteer workspace state (split-pane divider positions, recent timelines)
 * to {@code <projectRoot>/config/puppeteer/workspace.properties}.
 *
 * <p>All operations are best-effort — IO failures are swallowed and the editor continues
 * with default layout. Loading from a non-existent file returns an empty prefs instance.
 */
public final class PuppeteerWorkspacePrefs {
    public static final String DIVIDER_TOP = "divider.top";          // left pane vs preview
    public static final String DIVIDER_BOTTOM = "divider.bottom";    // keyframe editor vs timeline
    public static final String DIVIDER_CONTENT = "divider.content";  // top half vs bottom half
    public static final String DIVIDER_CODE_PANE = "divider.codePane"; // workspace vs code pane

    public static final String KEY_VIEWPORT_PAN_X = "viewport.panX";
    public static final String KEY_VIEWPORT_PAN_Y = "viewport.panY";
    public static final String KEY_VIEWPORT_ZOOM = "viewport.zoom";
    public static final String KEY_TIMELINE_PLAYHEAD = "timeline.playhead";

    private static final String RECENT_KEY = "recent.timelines";
    private static final String RECENT_RECORD_DELIM = ";;";
    private static final String RECENT_FIELD_DELIM = "||";
    private static final int MAX_RECENT = 12;

    private final File projectRoot;
    private final Map<String, String> entries = new LinkedHashMap<>();
    private final List<RecentTimeline> recent = new ArrayList<>();

    private PuppeteerWorkspacePrefs(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    public static PuppeteerWorkspacePrefs load(File projectRoot) {
        PuppeteerWorkspacePrefs prefs = new PuppeteerWorkspacePrefs(projectRoot);
        File file = resolveFile(projectRoot);
        if (file == null || !file.isFile()) return prefs;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException ignored) {
            return prefs;
        }
        for (String name : props.stringPropertyNames()) {
            prefs.entries.put(name, props.getProperty(name));
        }
        prefs.parseRecent(props.getProperty(RECENT_KEY, ""));
        return prefs;
    }

    public Optional<Double> getDivider(String key) {
        String raw = entries.get(key);
        if (raw == null) return Optional.empty();
        try {
            double v = Double.parseDouble(raw.trim());
            if (v < 0.0 || v > 1.0 || Double.isNaN(v)) return Optional.empty();
            return Optional.of(v);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void setDivider(String key, double value) {
        if (key == null || key.isBlank()) return;
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) return;
        entries.put(key, String.format(java.util.Locale.ROOT, "%.4f", value));
    }

    public Optional<Double> getDouble(String key) {
        String raw = entries.get(key);
        if (raw == null) return Optional.empty();
        try {
            double v = Double.parseDouble(raw.trim());
            if (Double.isNaN(v)) return Optional.empty();
            return Optional.of(v);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void setDouble(String key, double value) {
        if (key == null || key.isBlank()) return;
        if (Double.isNaN(value)) return;
        entries.put(key, String.format(java.util.Locale.ROOT, "%.4f", value));
    }

    public Optional<Long> getLong(String key) {
        String raw = entries.get(key);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public void setLong(String key, long value) {
        if (key == null || key.isBlank()) return;
        entries.put(key, Long.toString(value));
    }

    public List<RecentTimeline> getRecent() {
        return List.copyOf(recent);
    }

    public void pushRecent(String name, File jesFile) {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        String absPath = jesFile == null ? "" : jesFile.getAbsolutePath();
        recent.removeIf(r -> r.name().equalsIgnoreCase(trimmed));
        recent.add(0, new RecentTimeline(trimmed, absPath, System.currentTimeMillis()));
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.size() - 1);
        }
    }

    public boolean save() {
        if (projectRoot == null) return false;
        try {
            Path dir = projectRoot.toPath().resolve("config").resolve("puppeteer");
            Files.createDirectories(dir);
            Path file = dir.resolve("workspace.properties");
            Properties props = new Properties();
            props.putAll(entries);
            props.setProperty(RECENT_KEY, formatRecent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Puppeteer workspace preferences");
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void parseRecent(String raw) {
        recent.clear();
        if (raw == null || raw.isBlank()) return;
        for (String entry : raw.split(java.util.regex.Pattern.quote(RECENT_RECORD_DELIM))) {
            String[] parts = entry.split(java.util.regex.Pattern.quote(RECENT_FIELD_DELIM));
            if (parts.length < 1) continue;
            String name = parts[0].trim();
            if (name.isEmpty()) continue;
            String path = parts.length > 1 ? parts[1].trim() : "";
            long lastUsed = 0L;
            if (parts.length > 2) {
                try { lastUsed = Long.parseLong(parts[2].trim()); } catch (NumberFormatException ignored) {}
            }
            recent.add(new RecentTimeline(name, path, lastUsed));
        }
    }

    private String formatRecent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recent.size(); i++) {
            if (i > 0) sb.append(RECENT_RECORD_DELIM);
            RecentTimeline r = recent.get(i);
            sb.append(r.name()).append(RECENT_FIELD_DELIM)
              .append(r.absolutePath() == null ? "" : r.absolutePath())
              .append(RECENT_FIELD_DELIM)
              .append(r.lastUsedMs());
        }
        return sb.toString();
    }

    private static File resolveFile(File projectRoot) {
        if (projectRoot == null) return null;
        return projectRoot.toPath()
            .resolve("config").resolve("puppeteer").resolve("workspace.properties").toFile();
    }

    public record RecentTimeline(String name, String absolutePath, long lastUsedMs) {
        public File file() {
            return absolutePath == null || absolutePath.isBlank() ? null : new File(absolutePath);
        }
    }
}
