package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PuppeteerAudioLibrary {
    static final String IMPORT_RELATIVE_DIR = "assets/audio/puppeteer";

    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
        "aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm"
    );

    private PuppeteerAudioLibrary() {
    }

    static List<AudioEntry> scan(File projectRoot) {
        List<AudioEntry> out = new ArrayList<>();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return out;
        }
        List<File> files = new ArrayList<>();
        collectAudio(projectRoot, files, 0);
        for (File file : files) {
            String relativePath = projectRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            out.add(new AudioEntry(relativePath, stripExtension(file.getName()), file));
        }
        out.sort((a, b) -> a.relativePath().compareToIgnoreCase(b.relativePath()));
        return out;
    }

    static Path resolveUniqueImportTarget(Path importDir, String fileName) {
        if (importDir == null) {
            return Path.of(fileName == null ? "audio" : fileName);
        }
        String normalizedName = sanitizeFileName(fileName);
        if (normalizedName.isBlank()) normalizedName = "audio";
        Path target = importDir.resolve(normalizedName);
        if (!Files.exists(target)) return target;

        String stem = normalizedName;
        String ext = "";
        int dot = normalizedName.lastIndexOf('.');
        if (dot > 0) {
            stem = normalizedName.substring(0, dot);
            ext = normalizedName.substring(dot);
        }
        int index = 2;
        while (true) {
            Path candidate = importDir.resolve(stem + "-" + index + ext);
            if (!Files.exists(candidate)) return candidate;
            index++;
        }
    }

    static boolean isAudioFile(String fileName) {
        return AUDIO_EXTENSIONS.contains(extensionOf(fileName));
    }

    private static void collectAudio(File dir, List<File> out, int depth) {
        if (dir == null || out == null || depth > 10) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                if (name.startsWith(".") || name.equals("build") || name.equals("bin") || name.equals("node_modules")) {
                    continue;
                }
                collectAudio(file, out, depth + 1);
            } else if (isAudioFile(file.getName())) {
                out.add(file);
            }
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String stripExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        String normalized = fileName.trim().replace('\\', '_').replace('/', '_');
        return normalized.isBlank() ? "" : normalized;
    }

    record AudioEntry(String relativePath, String baseName, File file) {
    }
}
