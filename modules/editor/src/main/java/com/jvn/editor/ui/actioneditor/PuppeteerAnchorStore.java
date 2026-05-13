package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Persists named anchors (from {@link AnchorEditor}) to
 * {@code <projectRoot>/config/puppeteer/anchors.properties}.
 *
 * <p>All operations are best-effort — IO failures are swallowed. The file is
 * flat-properties encoded so it can be inspected and version-controlled easily.
 *
 * <p>Format per anchor:
 * <pre>
 * anchor.count=N
 * anchor.0.entity=arm
 * anchor.0.name=elbow
 * anchor.0.x=0.512345
 * anchor.0.y=0.678901
 * anchor.0.relative=true
 * </pre>
 */
public final class PuppeteerAnchorStore {

    private static final String FILENAME = "anchors.properties";

    private PuppeteerAnchorStore() {}

    // ── Save ─────────────────────────────────────────────────────────────────

    /**
     * Saves all named anchors from {@code project} to disk.  No-op if
     * {@code projectRoot} is null or the project has no anchors.
     */
    public static void save(File projectRoot, AnimationProject project) {
        if (projectRoot == null || project == null) return;
        Map<String, Map<String, Anchor>> allAnchors = project.getEntityAnchorsView();
        if (allAnchors == null || allAnchors.isEmpty()) {
            deleteFile(projectRoot);
            return;
        }

        Properties props = new Properties();
        int idx = 0;
        for (Map.Entry<String, Map<String, Anchor>> entityEntry : allAnchors.entrySet()) {
            String entityName = entityEntry.getKey();
            Map<String, Anchor> anchors = entityEntry.getValue();
            if (anchors == null || anchors.isEmpty()) continue;
            for (Anchor a : anchors.values()) {
                String pfx = "anchor." + idx;
                props.setProperty(pfx + ".entity",   escape(entityName));
                props.setProperty(pfx + ".name",     escape(a.getName()));
                props.setProperty(pfx + ".x",        Double.toString(a.getX()));
                props.setProperty(pfx + ".y",        Double.toString(a.getY()));
                props.setProperty(pfx + ".relative", Boolean.toString(a.isRelative()));
                idx++;
            }
        }
        props.setProperty("anchor.count", Integer.toString(idx));

        try {
            Path dir = projectRoot.toPath().resolve("config").resolve("puppeteer");
            Files.createDirectories(dir);
            try (OutputStream out = Files.newOutputStream(dir.resolve(FILENAME))) {
                props.store(out, "Puppeteer named anchors");
            }
        } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    /**
     * Loads named anchors from disk and applies them to {@code project} via
     * {@link AnimationProject#setAnchor}.  Clears all existing named anchors
     * first so the on-disk state is the source of truth.
     */
    public static void load(File projectRoot, AnimationProject project) {
        if (projectRoot == null || project == null) return;
        File file = resolveFile(projectRoot);
        if (file == null || !file.isFile()) return;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
            return;
        }

        int count = parseInt(props.getProperty("anchor.count"), 0);
        if (count <= 0) return;

        project.clearAllAnchors();
        for (int i = 0; i < count; i++) {
            String pfx  = "anchor." + i;
            String entity = unescape(props.getProperty(pfx + ".entity", ""));
            String name   = unescape(props.getProperty(pfx + ".name", ""));
            if (entity.isEmpty() || name.isEmpty()) continue;
            double x        = parseDouble(props.getProperty(pfx + ".x"), 0.5);
            double y        = parseDouble(props.getProperty(pfx + ".y"), 0.5);
            boolean relative = Boolean.parseBoolean(props.getProperty(pfx + ".relative", "true"));
            project.setAnchor(entity, new Anchor(name, x, y, relative));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void deleteFile(File projectRoot) {
        try {
            File f = resolveFile(projectRoot);
            if (f != null && f.isFile()) f.delete();
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
    }

    private static File resolveFile(File projectRoot) {
        if (projectRoot == null) return null;
        return projectRoot.toPath()
            .resolve("config").resolve("puppeteer").resolve(FILENAME).toFile();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\=", "=").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }
}
