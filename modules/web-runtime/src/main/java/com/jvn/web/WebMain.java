package com.jvn.web;

import org.jspecify.annotations.Nullable;
import org.teavm.jso.JSExport;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * TeaVM browser entrypoint used by {@code generateJavaScript}.
 */
public final class WebMain {
  static final String CANVAS_ELEMENT_ID = "jvn-canvas";
  static final String CONFIG_ELEMENT_ID = "jvn-config";
  static final String STATUS_ELEMENT_ID = "jvn-status";

  private static @Nullable WebRuntimeSession activeSession;

  private WebMain() {}

  public static void main(String[] args) {
    HTMLDocument document = HTMLDocument.current();
    HTMLElement configElement = document.getElementById(CONFIG_ELEMENT_ID);
    String configJson = configElement == null ? "" : configElement.getTextContent();
    HTMLElement statusElement = document.getElementById(STATUS_ELEMENT_ID);

    try {
      WebRuntimeSession session = WebLauncher.startGame(configJson, CANVAS_ELEMENT_ID);
      activeSession = session;
      document.setTitle(session.engine().getConfig().title() + " — JVN Web");
      setStatus(statusElement, "Engine loop online · Canvas 2D bootstrap");
    } catch (RuntimeException error) {
      setStatus(statusElement, "JVN web startup failed: " + safeMessage(error));
      throw error;
    }
  }

  /**
   * Debug-only hook the {@code webSmoke} Node harness calls (as {@code exports.testAdvanceFromClick()})
   * to synthesize a dialogue-advance click. Real browser click/input routing (sub-project 3)
   * doesn't exist yet, so this is the harness's only way to reach a scene's choice node for now
   * — not a general-purpose runtime API, and not meant to survive once real input routing lands.
   */
  @JSExport
  public static void testAdvanceFromClick() {
    if (activeSession == null || activeSession.vnScene() == null) return;
    activeSession.vnScene().advanceFromClick();
  }

  private static void setStatus(HTMLElement element, String status) {
    if (element != null) element.setInnerText(status);
  }

  private static String safeMessage(RuntimeException error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }
}
