package com.jvn.web;

import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * TeaVM browser entrypoint used by {@code generateJavaScript}.
 */
public final class WebMain {
  static final String CANVAS_ELEMENT_ID = "jvn-canvas";
  static final String CONFIG_ELEMENT_ID = "jvn-config";
  static final String STATUS_ELEMENT_ID = "jvn-status";

  private WebMain() {}

  public static void main(String[] args) {
    HTMLDocument document = HTMLDocument.current();
    HTMLElement configElement = document.getElementById(CONFIG_ELEMENT_ID);
    String configJson = configElement == null ? "" : configElement.getTextContent();
    HTMLElement statusElement = document.getElementById(STATUS_ELEMENT_ID);

    try {
      WebRuntimeSession session = WebLauncher.startGame(configJson, CANVAS_ELEMENT_ID);
      document.setTitle(session.engine().getConfig().title() + " — JVN Web");
      setStatus(statusElement, "Engine loop online · Canvas 2D bootstrap");
    } catch (RuntimeException error) {
      setStatus(statusElement, "JVN web startup failed: " + safeMessage(error));
      throw error;
    }
  }

  private static void setStatus(HTMLElement element, String status) {
    if (element != null) element.setInnerText(status);
  }

  private static String safeMessage(RuntimeException error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }
}
