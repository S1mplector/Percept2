package com.jvn.editor;

import com.jvn.core.diagnostics.GraphicsPipeline;
import java.util.Locale;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;

/** User-facing status for the JavaFX graphics pipeline selected at startup. */
final class EditorGraphicsStatus {
  record Snapshot(boolean hardwareAccelerated, String chipText, String tooltip) {}

  private EditorGraphicsStatus() {}

  static Snapshot detect() {
    return classify(
        Platform.isSupported(ConditionalFeature.SCENE3D),
        GraphicsPipeline.requestedMode(),
        System.getProperty("os.name", ""));
  }

  static Snapshot classify(
      boolean hardwareFeaturesAvailable,
      GraphicsPipeline.Mode requestedMode,
      String operatingSystem) {
    GraphicsPipeline.Mode mode =
        requestedMode == null ? GraphicsPipeline.Mode.AUTO : requestedMode;
    if (hardwareFeaturesAvailable) {
      return new Snapshot(
          true,
          "GPU active",
          "JavaFX hardware rendering is active. " + GraphicsPipeline.statusText() + ".");
    }
    if (mode == GraphicsPipeline.Mode.SOFTWARE) {
      return new Snapshot(
          false,
          "GPU off",
          "JavaFX software rendering was selected in Editor Settings. "
              + "Choose Prefer GPU acceleration and restart JVN to enable hardware rendering.");
    }

    boolean linux = operatingSystem != null
        && operatingSystem.toLowerCase(Locale.ROOT).contains("linux");
    String recovery = linux
        ? " Verify `glxinfo -B` succeeds and restart after installing or updating GPU drivers."
        : " Verify the system graphics driver is installed and restart JVN.";
    return new Snapshot(
        false,
        "GPU fallback",
        "JavaFX hardware rendering is unavailable, so complex editor windows may be rendered "
            + "in software." + recovery);
  }
}
