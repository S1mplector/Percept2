package com.jvn.editor.ui;

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
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Persists which VNS timeline blocks are folded, per script, to
 * {@code <projectRoot>/config/editor/timeline-folds.properties}.
 *
 * <p>All operations are best-effort — IO failures are swallowed and the editor continues
 * with no restored fold state. Loading from a non-existent file returns an empty store.
 */
public final class VnsFoldStateStore {
    private static final String BLOCK_DELIM = ";;";
    private static final String FIELD_DELIM = "||";

    private final File projectRoot;
    private final Map<String, List<FoldedBlockKey>> byScript = new LinkedHashMap<>();

    private VnsFoldStateStore(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    public static VnsFoldStateStore load(File projectRoot) {
        VnsFoldStateStore store = new VnsFoldStateStore(projectRoot);
        File file = resolveFile(projectRoot);
        if (file == null || !file.isFile()) return store;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            props.load(in);
        } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains empty
            return store;
        }
        for (String scriptPath : props.stringPropertyNames()) {
            store.byScript.put(scriptPath, parseBlocks(props.getProperty(scriptPath)));
        }
        return store;
    }

    public List<FoldedBlockKey> getFoldedBlocks(String scriptRelativePath) {
        if (scriptRelativePath == null) return List.of();
        return List.copyOf(byScript.getOrDefault(scriptRelativePath, List.of()));
    }

    public void setFoldedBlocks(String scriptRelativePath, List<FoldedBlockKey> keys) {
        if (scriptRelativePath == null || scriptRelativePath.isBlank()) return;
        if (keys == null || keys.isEmpty()) {
            byScript.remove(scriptRelativePath);
        } else {
            byScript.put(scriptRelativePath, List.copyOf(keys));
        }
    }

    public boolean save() {
        if (projectRoot == null) return false;
        try {
            Path dir = projectRoot.toPath().resolve("config").resolve("editor");
            Files.createDirectories(dir);
            Path file = dir.resolve("timeline-folds.properties");
            Properties props = new Properties();
            for (Map.Entry<String, List<FoldedBlockKey>> entry : byScript.entrySet()) {
                props.setProperty(entry.getKey(), formatBlocks(entry.getValue()));
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "VNS timeline fold state");
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static List<FoldedBlockKey> parseBlocks(String raw) {
        List<FoldedBlockKey> keys = new ArrayList<>();
        if (raw == null || raw.isBlank()) return keys;
        for (String entry : raw.split(Pattern.quote(BLOCK_DELIM))) {
            String[] parts = entry.split(Pattern.quote(FIELD_DELIM));
            if (parts.length < 2) continue;
            try {
                int ordinal = Integer.parseInt(parts[0].trim());
                String headerHash = parts[1].trim();
                if (headerHash.isEmpty()) continue;
                keys.add(new FoldedBlockKey(ordinal, headerHash));
            } catch (NumberFormatException ignored) {
                // reason: malformed numeric text input; skip this entry
            }
        }
        return keys;
    }

    private static String formatBlocks(List<FoldedBlockKey> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(BLOCK_DELIM);
            FoldedBlockKey key = keys.get(i);
            sb.append(key.ordinal()).append(FIELD_DELIM).append(key.headerHash());
        }
        return sb.toString();
    }

    private static File resolveFile(File projectRoot) {
        if (projectRoot == null) return null;
        return projectRoot.toPath()
            .resolve("config").resolve("editor").resolve("timeline-folds.properties").toFile();
    }

    /**
     * Identifies a timeline block by its document-order ordinal among timeline blocks
     * plus a short hash of its header line, so folds survive edits that shift line
     * numbers but drop cleanly if the block itself is gone or reordered.
     */
    public record FoldedBlockKey(int ordinal, String headerHash) {}
}
