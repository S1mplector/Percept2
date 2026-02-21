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
    try (InputStream in = open(scriptName)) {
      if (in == null) {
        throw new IOException("Script not found: " + scriptName);
      }
      return parser.parse(in);
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

    String locale = Localization.locale();
    LocalizedScriptLoader loader = new LocalizedScriptLoader(
        Thread.currentThread().getContextClassLoader(),
        scriptsBase
    );

    for (String path : loader.getCandidatePaths(scriptName, locale)) {
      if (path == null || path.isBlank()) continue;
      candidates.add(path);
      if (path.startsWith(scriptsBase)) {
        candidates.add(path.substring(scriptsBase.length()));
      }
    }

    // Direct fallback in case the provided key already matches the asset catalog key.
    candidates.add(scriptName);

    return new ArrayList<>(candidates);
  }
}
