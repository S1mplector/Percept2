package com.jvn.core.vn;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.localization.Localization;
import com.jvn.core.localization.LocalizedScriptLoader;
import com.jvn.core.vn.script.VnScriptParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Centralized VN scenario loader with localization-aware script resolution.
 */
public class VnScenarioLoader {
  private static final String DEFAULT_SCRIPTS_BASE = "game/scripts/";

  private final AssetCatalog assets;
  private final VnScriptParser parser;
  private final String scriptsBase;

  public VnScenarioLoader() {
    this(new AssetCatalog(), new VnScriptParser(), DEFAULT_SCRIPTS_BASE);
  }

  public VnScenarioLoader(AssetCatalog assets, VnScriptParser parser, String scriptsBase) {
    this.assets = assets == null ? new AssetCatalog() : assets;
    this.parser = parser == null ? new VnScriptParser() : parser;
    String base = scriptsBase == null || scriptsBase.isBlank() ? DEFAULT_SCRIPTS_BASE : scriptsBase;
    this.scriptsBase = base.endsWith("/") ? base : base + "/";
  }

  public VnScenario load(String scriptName) throws IOException {
    String normalized = scriptName == null ? "" : scriptName.trim();
    try (InputStream in = open(normalized)) {
      if (in == null) {
        throw new IOException("Script not found: " + normalized);
      }
      return parser.parse(in, normalized, includePath -> {
        InputStream included = open(includePath);
        if (included == null) {
          throw new IOException("Included script not found: " + includePath);
        }
        return included;
      });
    }
  }

  public InputStream open(String scriptName) {
    for (String candidate : localizedScriptCandidates(scriptName)) {
      try {
        InputStream in = assets.open(AssetType.SCRIPT, candidate);
        if (in != null) return in;
      } catch (Exception ignored) {
      }
    }
    return null;
  }

  public List<String> localizedScriptCandidates(String scriptName) {
    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    if (scriptName == null || scriptName.isBlank()) return List.of();

    String normalized = scriptName.trim();
    String locale = Localization.locale();
    LocalizedScriptLoader loader = new LocalizedScriptLoader(
        Thread.currentThread().getContextClassLoader(),
        scriptsBase
    );

    for (String path : loader.getCandidatePaths(normalized, locale)) {
      if (path == null || path.isBlank()) continue;
      candidates.add(path);
      if (path.startsWith(scriptsBase)) {
        candidates.add(path.substring(scriptsBase.length()));
      }
    }

    // Backward compatibility: deprecated runtime demo script key now maps to resolved entry script.
    if ("demo.vns".equalsIgnoreCase(normalized)) {
      String mappedEntry = VnEntryScriptResolver.resolveEntryScript(null, null);
      if (mappedEntry == null || mappedEntry.isBlank()) {
        mappedEntry = "story/prologue.vns";
      }
      for (String path : loader.getCandidatePaths(mappedEntry, locale)) {
        if (path == null || path.isBlank()) continue;
        candidates.add(path);
        if (path.startsWith(scriptsBase)) {
          candidates.add(path.substring(scriptsBase.length()));
        }
      }
      candidates.add(mappedEntry);
      if (!"story/prologue.vns".equalsIgnoreCase(mappedEntry)) {
        for (String path : loader.getCandidatePaths("story/prologue.vns", locale)) {
          if (path == null || path.isBlank()) continue;
          candidates.add(path);
          if (path.startsWith(scriptsBase)) {
            candidates.add(path.substring(scriptsBase.length()));
          }
        }
        candidates.add("story/prologue.vns");
        candidates.add("prologue.vns");
      }
    }

    // Direct fallback in case the provided key already matches the asset catalog key.
    candidates.add(normalized);

    return new ArrayList<>(candidates);
  }
}
