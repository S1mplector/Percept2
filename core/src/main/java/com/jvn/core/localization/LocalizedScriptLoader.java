package com.jvn.core.localization;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Locale-aware script loader for VN dialogue.
 * Supports loading localized versions of VNS scripts with fallback to default.
 *
 * Script resolution order for locale "ja":
 *   1. scripts/example.ja.vns
 *   2. scripts/ja/example.vns
 *   3. scripts/example.vns (fallback)
 */
public final class LocalizedScriptLoader {
    private final ClassLoader loader;
    private final String basePath;

    public LocalizedScriptLoader(ClassLoader loader) {
        this(loader, "game/scripts/");
    }

    public LocalizedScriptLoader(ClassLoader loader, String basePath) {
        this.loader = loader;
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
    }

    /**
     * Load a script with locale fallback.
     * @param scriptName Script name (e.g., "chapter1.vns")
     * @param locale Target locale (e.g., "ja", "de", "fr")
     * @return InputStream for the script, or null if not found
     */
    public InputStream load(String scriptName, String locale) throws IOException {
        for (String path : getCandidatePaths(scriptName, locale)) {
            InputStream in = loader.getResourceAsStream(path);
            if (in != null) return in;
        }
        return null;
    }

    /**
     * Load a script using the current global locale.
     */
    public InputStream load(String scriptName) throws IOException {
        return load(scriptName, Localization.locale());
    }

    /**
     * Check if a localized version exists for the given script and locale.
     */
    public boolean hasLocalizedVersion(String scriptName, String locale) {
        List<String> paths = getCandidatePaths(scriptName, locale);
        // Skip the last one (fallback) when checking for localized versions
        for (int i = 0; i < paths.size() - 1; i++) {
            if (loader.getResourceAsStream(paths.get(i)) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get candidate paths for a script in order of preference.
     */
    public List<String> getCandidatePaths(String scriptName, String locale) {
        List<String> paths = new ArrayList<>();
        String nameWithoutExt = scriptName.contains(".") 
            ? scriptName.substring(0, scriptName.lastIndexOf('.'))
            : scriptName;
        String ext = scriptName.contains(".")
            ? scriptName.substring(scriptName.lastIndexOf('.'))
            : ".vns";

        if (locale != null && !locale.isBlank() && !locale.equals("en")) {
            // 1. scripts/example.ja.vns
            paths.add(basePath + nameWithoutExt + "." + locale + ext);
            // 2. scripts/ja/example.vns
            paths.add(basePath + locale + "/" + scriptName);
        }
        // 3. scripts/example.vns (fallback)
        paths.add(basePath + scriptName);

        return paths;
    }

    /**
     * Get all available locales for a script by scanning common locale codes.
     */
    public List<String> getAvailableLocales(String scriptName) {
        List<String> available = new ArrayList<>();
        String[] commonLocales = {"en", "ja", "zh", "ko", "de", "fr", "es", "it", "pt", "ru"};
        
        for (String locale : commonLocales) {
            List<String> paths = getCandidatePaths(scriptName, locale);
            for (String path : paths) {
                if (loader.getResourceAsStream(path) != null) {
                    if (!available.contains(locale)) {
                        available.add(locale);
                    }
                    break;
                }
            }
        }
        return available;
    }
}
