package com.jvn.core.vn;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.localization.Localization;
import com.jvn.core.localization.LocalizedScriptLoader;
import com.jvn.core.vn.script.VnScriptParser;

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
    OpenResult opened = openWithDiagnostics(normalized);
    try (InputStream in = opened.stream) {
      if (in == null) {
        throw scriptNotFound(normalized, opened);
      }
      return parser.parse(in, normalized, includePath -> {
        OpenResult inc = openWithDiagnostics(includePath);
        if (inc.stream == null) {
          throw scriptNotFound("Included script not found: " + includePath, inc);
        }
        return inc.stream;
      });
    }
  }

  public InputStream open(String scriptName) {
    return openWithDiagnostics(scriptName).stream;
  }

  /**
   * Probe the candidate paths; on success returns the first openable stream.
   * On failure, returns the most informative exception encountered (if any) so
   * callers can surface a real cause rather than a bare "not found".
   */
  private OpenResult openWithDiagnostics(String scriptName) {
    Exception firstFailure = null;
    String firstFailingCandidate = null;
    List<String> tried = new ArrayList<>();
    for (String candidate : localizedScriptCandidates(scriptName)) {
      tried.add(candidate);
      try {
        InputStream in = assets.open(AssetType.SCRIPT, candidate);
        if (in != null) return new OpenResult(in, null, null, tried);
      } catch (Exception ex) {
        if (firstFailure == null) {
          firstFailure = ex;
          firstFailingCandidate = candidate;
        }
      }
    }
    return new OpenResult(null, firstFailure, firstFailingCandidate, tried);
  }

  private static IOException scriptNotFound(String prefix, OpenResult opened) {
    StringBuilder msg = new StringBuilder();
    if (prefix != null && !prefix.isBlank()) {
      msg.append(prefix);
    } else {
      msg.append("Script not found");
    }
    if (opened != null && opened.firstFailure != null) {
      msg.append(" (failed to open '")
         .append(opened.firstFailingCandidate)
         .append("': ")
         .append(opened.firstFailure.getClass().getSimpleName());
      String causeMsg = opened.firstFailure.getMessage();
      if (causeMsg != null && !causeMsg.isBlank()) msg.append(": ").append(causeMsg);
      msg.append(')');
    }
    if (opened != null && !opened.triedCandidates.isEmpty()) {
      msg.append(". Tried: ").append(opened.triedCandidates);
    }
    IOException io = new IOException(msg.toString());
    if (opened != null && opened.firstFailure != null) io.initCause(opened.firstFailure);
    return io;
  }

  private static final class OpenResult {
    final InputStream stream;
    final Exception firstFailure;
    final String firstFailingCandidate;
    final List<String> triedCandidates;
    OpenResult(InputStream stream, Exception firstFailure, String firstFailingCandidate, List<String> triedCandidates) {
      this.stream = stream;
      this.firstFailure = firstFailure;
      this.firstFailingCandidate = firstFailingCandidate;
      this.triedCandidates = triedCandidates == null ? List.of() : List.copyOf(triedCandidates);
    }
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
