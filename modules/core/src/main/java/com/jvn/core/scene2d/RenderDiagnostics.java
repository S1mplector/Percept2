package com.jvn.core.scene2d;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Warn-once diagnostics for optional renderer operations that would otherwise disappear silently. */
public final class RenderDiagnostics {
  private static final Logger log = LoggerFactory.getLogger(RenderDiagnostics.class);
  private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

  private RenderDiagnostics() {}

  public static void unsupported(Blitter2D renderer, RenderFeature feature, String operation) {
    String rendererName = renderer.getCapabilities().rendererName();
    String key = rendererName + ':' + feature + ':' + operation;
    if (REPORTED.add(key)) {
      log.warn("Renderer '{}' does not support {} (operation '{}'); the operation was skipped",
          rendererName, feature, operation);
    }
  }

  /**
   * Warn-once for an asset (image, video, etc.) that failed to load. {@code context} disambiguates
   * repeated uses of the same path (e.g. a distinct sprite layer or UI slot) so each is reported once.
   */
  public static void missingAsset(String path, String context) {
    if (path == null || path.isBlank()) return;
    String key = "asset:" + path + ':' + context;
    if (REPORTED.add(key)) {
      log.warn("Missing asset '{}'{}", path, context == null ? "" : " (" + context + ")");
    }
  }

  /** Clears warning de-duplication state. Intended for isolated tests and renderer reinitialization. */
  public static void reset() {
    REPORTED.clear();
  }
}
