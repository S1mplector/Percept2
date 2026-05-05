package com.jvn.core.localization;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Locale-aware script loader for VN dialogue.
 *
 * <p>Supports loading localised versions of VNS scripts with automatic
 * fallback to the default (unlocalized) file. For a given script name
 * and locale, the resolution order is:</p>
 *
 * <ol>
 *   <li>{@code scripts/example.<locale>.vns} — locale-suffixed file</li>
 *   <li>{@code scripts/<locale>/example.vns} — locale subdirectory</li>
 *   <li>{@code scripts/example.vns} — fallback (always tried last)</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * LocalizedScriptLoader scripts = new LocalizedScriptLoader(loader);
 * try (InputStream in = scripts.load("chapter1.vns", "ja")) {
 *     // read localized (or fallback) script
 * }
 * }</pre>
 *
 * @see Localization
 */
public final class LocalizedScriptLoader {

    /** Classloader used to resolve script resources. */
    private final ClassLoader loader;

    /** Base classpath directory for scripts (always ends with {@code /}). */
    private final String basePath;

    /**
     * Create a loader using the default script base path ({@code game/scripts/}).
     *
     * @param loader the classloader for resource resolution
     */
    public LocalizedScriptLoader(ClassLoader loader) {
        this(loader, "game/scripts/");
    }

    /**
     * Create a loader with a custom base path.
     *
     * @param loader   the classloader for resource resolution
     * @param basePath the classpath directory containing scripts
     */
    public LocalizedScriptLoader(ClassLoader loader, String basePath) {
        this.loader = loader;
        this.basePath = basePath.endsWith("/") ? basePath : basePath + "/";
    }

    /**
     * Load a script with locale fallback.
     *
     * @param scriptName script file name (e.g. "chapter1.vns")
     * @param locale     target locale code (e.g. "ja", "de", "fr")
     * @return an {@link InputStream} for the best matching script,
     *         or {@code null} if no candidate was found
     * @throws IOException if an I/O error occurs while opening the stream
     */
    public InputStream load(String scriptName, String locale) throws IOException {
        for (String path : getCandidatePaths(scriptName, locale)) {
            InputStream in = loader.getResourceAsStream(path);
            if (in != null) return in;
        }
        return null;
    }

    /**
     * Load a script using the current global locale from
     * {@link Localization#locale()}.
     *
     * @param scriptName script file name
     * @return an {@link InputStream} or {@code null}
     * @throws IOException if an I/O error occurs
     */
    public InputStream load(String scriptName) throws IOException {
        return load(scriptName, Localization.locale());
    }

    /**
     * Check whether a locale-specific version of the script exists
     * (ignoring the universal fallback).
     *
     * @param scriptName script file name
     * @param locale     target locale code
     * @return {@code true} if a localized variant was found
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
     * Build the ordered list of candidate classpath resources for a
     * script and locale.
     *
     * @param scriptName script file name
     * @param locale     target locale code
     * @return ordered list of classpath paths to try
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
     * Probe a set of common locale codes and return those for which at
     * least one candidate resource exists.
     *
     * @param scriptName script file name
     * @return list of available locale codes
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
